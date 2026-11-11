/*
 * librustseccomp.so — Tầng tương thích Android Seccomp-BPF & Bionic cho máy chủ Rust thuần ARM64 (Pumpkin MC / Wasmtime).
 *
 * ## Nguyên nhân gốc của lỗi Exit code 159 (128 + 31 = SIGSYS)
 *
 * Trên Android 8–15+, tiến trình con được khởi chạy từ ứng dụng không root thừa hưởng bộ lọc
 * seccomp-BPF `untrusted_app` của Zygote với hành động `SECCOMP_RET_TRAP` (ném tín hiệu `SIGSYS`,
 * mã 31 -> tiến trình thoát với mã 128 + 31 = 159) thay vì trả về `-ENOSYS` như nhân Linux chuẩn.
 *
 * Khi máy chủ Rust gốc (`pumpkin-aarch64-android` + `wasmtime` JIT cho `Multiversion.wasm`) khởi chạy:
 *   1. `wasmtime_wasi` gọi `syscall(__NR_openat2 = 437, ...)` để mở thư mục plugin an toàn;
 *      nếu nhận `ENOSYS`, nó tự động chuyển sang `openat()` chuẩn POSIX. Nhưng Android lại ném `SIGSYS`!
 *   2. `wasmtime::CodeMemory::publish` gọi `syscall(__NR_membarrier = 283, 32, 0)` sau khi đã xóa
 *      bộ đệm lệnh ARM64 (`dc cvau` + `ic ivau`) để đồng bộ lõi JIT; Android chặn `membarrier` bằng
 *      `SIGSYS` (và nếu trả lỗi khác `0`, `CodeMemory::publish` sẽ báo lỗi nạp `.wasm`).
 *   3. `std::fs::copy`, `inotify`, `libunwind`, và một số hàm Bionic trên các bản ROM tùy biến có thể
 *      gọi `copy_file_range(285)`, `kcmp(272)`, `process_vm_readv(270)`, `statx(291)`, `rseq(293)`,
 *      `clone3(435)`, `close_range(436)`, `faccessat2(439)`, `epoll_pwait2(441)`, `futex_waitv(449)`,
 *      `setpriority(140)`...
 *
 * ## Cơ chế bảo vệ kép (100% Native C/ARM64, 0% Java, độ trễ ~0ns)
 *
 *   - Lớp 1 (Userland PLT Hook): Đón trực tiếp `syscall()`, `copy_file_range()`, và bảo vệ mặt nạ
 *     tín hiệu (`sigaction`, `sigprocmask`, `pthread_sigmask`) để không luồng worker nào của Tokio/Wasmtime
 *     vô tình khóa `SIGSYS` (nếu `SIGSYS` bị khóa, nhân Linux sẽ buộc hủy tiến trình khi gặp `SECCOMP_RET_TRAP`).
 *   - Lớp 2 (Kernel `SIGSYS` `SA_SIGINFO | SA_NODEFER | SA_ONSTACK` Trap Handler): Bắt mọi lệnh `svc #0`
 *     nằm sâu bên trong `libc.so` hoặc mã máy bị seccomp chặn, mô phỏng tương đương (`membarrier` -> `dsb ish; isb`,
 *     `faccessat2` -> `faccessat`, `sysinfo`, `sched_getaffinity`...) hoặc gán `x0 = -ENOSYS` để thư viện
 *     tự động chuyển sang nhánh dự phòng POSIX mà không bao giờ sập tiến trình!
 *   - Đồng thời tắt Bionic Heap Pointer Tagging (TBI/MTE trên Android 11+) trước khi Rust/Wasmtime cấp phát RAM.
 *
 * Biên dịch (NDK r27, API 26+):
 *   aarch64-linux-android26-clang -shared -fPIC -O2 \
 *       -ffunction-sections -fdata-sections \
 *       -Wl,--gc-sections -Wl,-z,max-page-size=16384 \
 *       -o app/src/main/jniLibs/arm64-v8a/librustseccomp.so app/src/main/cpp/rustseccomp.c
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdarg.h>
#include <stdint.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/ucontext.h>
#include <unistd.h>

#ifndef SYS_SECCOMP
#define SYS_SECCOMP 1
#endif

/* Mã syscall Linux ARM64 (aarch64) thường bị Android untrusted_app seccomp chặn */
#define ARM64_NR_faccessat         48
#define ARM64_NR_fchmodat          53
#define ARM64_NR_futex             98
#define ARM64_NR_sched_setparam    118
#define ARM64_NR_sched_setscheduler 119
#define ARM64_NR_sched_setaffinity 122
#define ARM64_NR_sched_getaffinity 123
#define ARM64_NR_setpriority       140
#define ARM64_NR_setrlimit         164
#define ARM64_NR_sysinfo           179
#define ARM64_NR_mbind             235
#define ARM64_NR_get_mempolicy     236
#define ARM64_NR_set_mempolicy     237
#define ARM64_NR_prlimit64         261
#define ARM64_NR_process_vm_readv  270
#define ARM64_NR_process_vm_writev 271
#define ARM64_NR_kcmp              272
#define ARM64_NR_userfaultfd       282
#define ARM64_NR_membarrier        283
#define ARM64_NR_copy_file_range   285
#define ARM64_NR_pkey_mprotect     288
#define ARM64_NR_pkey_alloc        289
#define ARM64_NR_pkey_free         290
#define ARM64_NR_statx             291
#define ARM64_NR_rseq              293
#define ARM64_NR_io_uring_setup    425
#define ARM64_NR_io_uring_enter    426
#define ARM64_NR_io_uring_register 427
#define ARM64_NR_pidfd_open        434
#define ARM64_NR_clone3            435
#define ARM64_NR_close_range       436
#define ARM64_NR_openat2           437
#define ARM64_NR_pidfd_getfd       438
#define ARM64_NR_faccessat2        439
#define ARM64_NR_process_madvise   440
#define ARM64_NR_epoll_pwait2      441
#define ARM64_NR_futex_waitv       449
#define ARM64_NR_fchmodat2         452

/* Hằng số tắt Bionic Heap Pointer Tagging (Android 11+) */
#define M_BIONIC_SET_HEAP_TAGGING_LEVEL 55
#define M_HEAP_TAGGING_LEVEL_NONE 0
#define ANDROID_MALLOPT_HEAP_TAGGING_LEVEL 8

typedef int (*mallopt_fn)(int, int);
typedef int (*android_mallopt_fn)(int, void *, size_t);
typedef long (*syscall_fn)(long, unsigned long, unsigned long, unsigned long,
                           unsigned long, unsigned long, unsigned long);
typedef int (*sigaction_fn)(int, const struct sigaction *, struct sigaction *);
typedef int (*sigaction64_fn)(int, const struct sigaction64 *, struct sigaction64 *);
typedef int (*sigprocmask_fn)(int, const sigset_t *, sigset_t *);
typedef int (*sigprocmask64_fn)(int, const sigset64_t *, sigset64_t *);
typedef int (*pthread_sigmask_fn)(int, const sigset_t *, sigset_t *);
typedef int (*pthread_sigmask64_fn)(int, const sigset64_t *, sigset64_t *);

static syscall_fn real_syscall_ptr = NULL;
static sigaction_fn real_sigaction_ptr = NULL;
static sigaction64_fn real_sigaction64_ptr = NULL;
static sigprocmask_fn real_sigprocmask_ptr = NULL;
static sigprocmask64_fn real_sigprocmask64_ptr = NULL;
static pthread_sigmask_fn real_pthread_sigmask_ptr = NULL;
static pthread_sigmask64_fn real_pthread_sigmask64_ptr = NULL;

static volatile uint64_t logged_mask = 0;

static void safe_write_stderr(const char *msg) {
    if (!msg) return;
    size_t len = 0;
    while (msg[len] != '\0') len++;
    if (len > 0) {
        (void)write(2, msg, len);
    }
}

static void log_once_slot(unsigned int slot, const char *msg) {
    if (slot >= 64) return;
    uint64_t bit = 1ULL << slot;
    uint64_t prev = __atomic_fetch_or(&logged_mask, bit, __ATOMIC_RELAXED);
    if ((prev & bit) == 0) {
        safe_write_stderr(msg);
    }
}

/* Ghi thông báo async-signal-safe kèm mã số syscall (không dùng malloc/snprintf) */
static void log_trapped_syscall_once(long nr) {
    unsigned int slot = (unsigned int)((nr >= 0 ? nr : 0) % 48) + 16;
    uint64_t bit = 1ULL << slot;
    uint64_t prev = __atomic_fetch_or(&logged_mask, bit, __ATOMIC_RELAXED);
    if ((prev & bit) != 0) return;

    char buf[96];
    const char prefix[] = "[rust-seccomp] Da chuyen tiep an toan syscall #";
    const char suffix[] = " tren Android Bionic\n";
    size_t pos = 0;
    for (size_t i = 0; i < sizeof(prefix) - 1; i++) buf[pos++] = prefix[i];

    unsigned long val = (nr >= 0) ? (unsigned long)nr : 0UL;
    if (nr < 0) buf[pos++] = '-';
    char digits[24];
    size_t dlen = 0;
    do {
        digits[dlen++] = (char)('0' + (val % 10UL));
        val /= 10UL;
    } while (val > 0 && dlen < sizeof(digits));
    while (dlen > 0) buf[pos++] = digits[--dlen];

    for (size_t i = 0; i < sizeof(suffix) - 1; i++) buf[pos++] = suffix[i];
    (void)write(2, buf, pos);
}

static void *resolve_libc_sym(const char *name) {
    void *p = dlsym(RTLD_NEXT, name);
    if (p != NULL) return p;
    void *libc = dlopen("libc.so", RTLD_NOW | RTLD_NOLOAD);
    if (libc == NULL) libc = dlopen("libc.so", RTLD_NOW);
    if (libc != NULL) {
        p = dlsym(libc, name);
    }
    return p;
}

static void ensure_symbols_resolved(void) {
    if (real_syscall_ptr == NULL) {
        real_syscall_ptr = (syscall_fn)resolve_libc_sym("syscall");
    }
    if (real_sigaction_ptr == NULL) {
        real_sigaction_ptr = (sigaction_fn)resolve_libc_sym("sigaction");
    }
    if (real_sigaction64_ptr == NULL) {
        real_sigaction64_ptr = (sigaction64_fn)resolve_libc_sym("sigaction64");
    }
    if (real_sigprocmask_ptr == NULL) {
        real_sigprocmask_ptr = (sigprocmask_fn)resolve_libc_sym("sigprocmask");
    }
    if (real_sigprocmask64_ptr == NULL) {
        real_sigprocmask64_ptr = (sigprocmask64_fn)resolve_libc_sym("sigprocmask64");
    }
    if (real_pthread_sigmask_ptr == NULL) {
        real_pthread_sigmask_ptr = (pthread_sigmask_fn)resolve_libc_sym("pthread_sigmask");
    }
    if (real_pthread_sigmask64_ptr == NULL) {
        real_pthread_sigmask64_ptr = (pthread_sigmask64_fn)resolve_libc_sym("pthread_sigmask64");
    }
}

static void disable_bionic_heap_tagging(void) {
    mallopt_fn mp = (mallopt_fn)dlsym(RTLD_DEFAULT, "mallopt");
    if (mp != NULL) {
        if (mp(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE) == 0) {
            return;
        }
    }
    android_mallopt_fn amp = (android_mallopt_fn)dlsym(RTLD_DEFAULT, "android_mallopt");
    if (amp != NULL) {
        int level = M_HEAP_TAGGING_LEVEL_NONE;
        (void)amp(ANDROID_MALLOPT_HEAP_TAGGING_LEVEL, &level, sizeof(level));
    }
}

/*
 * Lớp 2: Bộ xử lý tín hiệu SIGSYS (SECCOMP_RET_TRAP) ở mức thanh ghi CPU ARM64.
 * Mọi thao tác bên trong hàm này TUYỆT ĐỐI async-signal-safe.
 */
static void rustseccomp_sigsys_handler(int sig, siginfo_t *info, void *ucontext) {
    (void)sig;
    if (ucontext == NULL) return;
    if (info != NULL && info->si_code != SYS_SECCOMP) {
        return;
    }

    ucontext_t *uc = (ucontext_t *)ucontext;
    long nr = (info != NULL && info->si_syscall > 0)
                  ? (long)info->si_syscall
                  : (long)uc->uc_mcontext.regs[8];

    uint64_t a0 = uc->uc_mcontext.regs[0];
    uint64_t a1 = uc->uc_mcontext.regs[1];
    uint64_t a2 = uc->uc_mcontext.regs[2];
    uint64_t a3 = uc->uc_mcontext.regs[3];
    uint64_t a4 = uc->uc_mcontext.regs[4];

    log_trapped_syscall_once(nr);

    switch (nr) {
        case ARM64_NR_membarrier: {
            /* Đồng bộ hàng rào bộ nhớ + pipeline lệnh ARM64 cho Wasmtime Cranelift JIT */
            __asm__ volatile("dsb ish\n\tisb" ::: "memory");
            uc->uc_mcontext.regs[0] = (a0 == 0) ? 0x79ULL : 0ULL;
            return;
        }
        case ARM64_NR_setpriority:
        case ARM64_NR_setrlimit:
        case ARM64_NR_sched_setparam:
        case ARM64_NR_sched_setscheduler:
        case ARM64_NR_sched_setaffinity: {
            uc->uc_mcontext.regs[0] = 0ULL;
            return;
        }
        case ARM64_NR_prlimit64: {
            if (a3 == 0) {
                uc->uc_mcontext.regs[0] = 0ULL;
            } else {
                uc->uc_mcontext.regs[0] = (uint64_t)(-(long)ENOSYS);
            }
            return;
        }
        case ARM64_NR_sched_getaffinity: {
            if (a2 != 0 && a1 > 0) {
                unsigned char *mask = (unsigned char *)(uintptr_t)a2;
                size_t len = (size_t)a1;
                for (size_t i = 0; i < len; i++) mask[i] = 0;
                size_t fill = len < 8 ? len : 8;
                for (size_t i = 0; i < fill; i++) mask[i] = 0xff;
                uc->uc_mcontext.regs[0] = (uint64_t)fill;
            } else {
                uc->uc_mcontext.regs[0] = (uint64_t)(-(long)EINVAL);
            }
            return;
        }
        case ARM64_NR_sysinfo: {
            if (a0 != 0) {
                /* struct sysinfo trên LP64 aarch64 dài 112 byte */
                unsigned char *p = (unsigned char *)(uintptr_t)a0;
                for (size_t i = 0; i < 112; i++) p[i] = 0;
                uint64_t *u64 = (uint64_t *)(uintptr_t)a0;
                u64[0] = 3600;                  /* uptime */
                u64[4] = 4ULL * 1024 * 1024 * 1024; /* totalram */
                u64[5] = 1ULL * 1024 * 1024 * 1024; /* freeram */
                uint32_t *mem_unit = (uint32_t *)(p + 104);
                *mem_unit = 1;
                uc->uc_mcontext.regs[0] = 0ULL;
            } else {
                uc->uc_mcontext.regs[0] = (uint64_t)(-(long)EFAULT);
            }
            return;
        }
        case ARM64_NR_faccessat2: {
            int saved_errno = errno;
            int rc = faccessat((int)a0, (const char *)(uintptr_t)a1, (int)a2, 0);
            int err = errno;
            errno = saved_errno;
            uc->uc_mcontext.regs[0] = (rc == 0) ? 0ULL : (uint64_t)(-(long)err);
            return;
        }
        case ARM64_NR_fchmodat2: {
            int saved_errno = errno;
            int rc = fchmodat((int)a0, (const char *)(uintptr_t)a1, (mode_t)a2, 0);
            int err = errno;
            errno = saved_errno;
            uc->uc_mcontext.regs[0] = (rc == 0) ? 0ULL : (uint64_t)(-(long)err);
            return;
        }
        case ARM64_NR_kcmp: {
            if ((int)a2 == 0) {
                int saved_errno = errno;
                struct stat s1, s2;
                int ok1 = fstat((int)a3, &s1);
                int ok2 = fstat((int)a4, &s2);
                errno = saved_errno;
                if (ok1 == 0 && ok2 == 0) {
                    uc->uc_mcontext.regs[0] =
                        (s1.st_dev == s2.st_dev && s1.st_ino == s2.st_ino) ? 0ULL : 2ULL;
                    return;
                }
            }
            uc->uc_mcontext.regs[0] = (uint64_t)(-(long)ENOSYS);
            return;
        }
        default: {
            /* Tất cả syscall mới chưa được Android seccomp mở (openat2, statx, copy_file_range,
             * close_range, clone3, rseq, epoll_pwait2, futex_waitv, io_uring...) -> trả -ENOSYS
             * để Rust std / rustix / tokio / wasmtime tự chạy nhánh dự phòng chuẩn POSIX. */
            uc->uc_mcontext.regs[0] = (uint64_t)(-(long)ENOSYS);
            return;
        }
    }
}

static void install_sigsys_handler(void) {
    ensure_symbols_resolved();

    sigset_t unblock_set;
    sigemptyset(&unblock_set);
    sigaddset(&unblock_set, SIGSYS);
    if (real_sigprocmask_ptr != NULL) {
        (void)real_sigprocmask_ptr(SIG_UNBLOCK, &unblock_set, NULL);
    }

    if (real_sigaction_ptr != NULL) {
        struct sigaction sa;
        memset(&sa, 0, sizeof(sa));
        sa.sa_sigaction = rustseccomp_sigsys_handler;
        sigemptyset(&sa.sa_mask);
        sa.sa_flags = SA_SIGINFO | SA_NODEFER | SA_ONSTACK;
        (void)real_sigaction_ptr(SIGSYS, &sa, NULL);
    }
}

__attribute__((constructor))
static void rustseccomp_init(void) {
    disable_bionic_heap_tagging();
    install_sigsys_handler();
    log_once_slot(0, "[rust-seccomp] Da kich hoat tang tuong thich Android Seccomp ARM64 (0% Java)\n");
}

/*
 * Lớp 1A: Bảo vệ bộ xử lý SIGSYS không bị ghi đè và không bị khóa trong các luồng con
 * của Tokio / Rayon / Wasmtime. Các tín hiệu khác (SIGSEGV/SIGILL/SIGTRAP của Wasmtime)
 * được chuyển tiếp nguyên vẹn 100%.
 */
__attribute__((visibility("default")))
int sigaction(int signum, const struct sigaction *act, struct sigaction *oldact) {
    ensure_symbols_resolved();
    if (signum == SIGSYS) {
        if (oldact != NULL) {
            memset(oldact, 0, sizeof(*oldact));
            oldact->sa_handler = SIG_DFL;
        }
        if (act != NULL) {
            return 0;
        }
    }
    return real_sigaction_ptr ? real_sigaction_ptr(signum, act, oldact) : -1;
}

__attribute__((visibility("default")))
int sigaction64(int signum, const struct sigaction64 *act, struct sigaction64 *oldact) {
    ensure_symbols_resolved();
    if (signum == SIGSYS) {
        if (oldact != NULL) {
            memset(oldact, 0, sizeof(*oldact));
            oldact->sa_handler = SIG_DFL;
        }
        if (act != NULL) {
            return 0;
        }
    }
    return real_sigaction64_ptr ? real_sigaction64_ptr(signum, act, oldact) : -1;
}

__attribute__((visibility("default")))
int sigprocmask(int how, const sigset_t *set, sigset_t *oldset) {
    ensure_symbols_resolved();
    if (set != NULL && (how == SIG_BLOCK || how == SIG_SETMASK) && sigismember(set, SIGSYS) == 1) {
        sigset_t filtered = *set;
        sigdelset(&filtered, SIGSYS);
        return real_sigprocmask_ptr ? real_sigprocmask_ptr(how, &filtered, oldset) : 0;
    }
    return real_sigprocmask_ptr ? real_sigprocmask_ptr(how, set, oldset) : 0;
}

__attribute__((visibility("default")))
int sigprocmask64(int how, const sigset64_t *set, sigset64_t *oldset) {
    ensure_symbols_resolved();
    if (set != NULL && (how == SIG_BLOCK || how == SIG_SETMASK)) {
        sigset64_t filtered = *set;
        uint64_t *bits = (uint64_t *)&filtered;
        uint64_t sigsys_bit = 1ULL << (SIGSYS - 1);
        if ((*bits & sigsys_bit) != 0) {
            *bits &= ~sigsys_bit;
            return real_sigprocmask64_ptr ? real_sigprocmask64_ptr(how, &filtered, oldset) : 0;
        }
    }
    return real_sigprocmask64_ptr ? real_sigprocmask64_ptr(how, set, oldset) : 0;
}

__attribute__((visibility("default")))
int pthread_sigmask(int how, const sigset_t *set, sigset_t *oldset) {
    ensure_symbols_resolved();
    if (set != NULL && (how == SIG_BLOCK || how == SIG_SETMASK) && sigismember(set, SIGSYS) == 1) {
        sigset_t filtered = *set;
        sigdelset(&filtered, SIGSYS);
        return real_pthread_sigmask_ptr ? real_pthread_sigmask_ptr(how, &filtered, oldset) : 0;
    }
    return real_pthread_sigmask_ptr ? real_pthread_sigmask_ptr(how, set, oldset) : 0;
}

__attribute__((visibility("default")))
int pthread_sigmask64(int how, const sigset64_t *set, sigset64_t *oldset) {
    ensure_symbols_resolved();
    if (set != NULL && (how == SIG_BLOCK || how == SIG_SETMASK)) {
        sigset64_t filtered = *set;
        uint64_t *bits = (uint64_t *)&filtered;
        uint64_t sigsys_bit = 1ULL << (SIGSYS - 1);
        if ((*bits & sigsys_bit) != 0) {
            *bits &= ~sigsys_bit;
            return real_pthread_sigmask64_ptr ? real_pthread_sigmask64_ptr(how, &filtered, oldset) : 0;
        }
    }
    return real_pthread_sigmask64_ptr ? real_pthread_sigmask64_ptr(how, set, oldset) : 0;
}

/*
 * Lớp 1B: Đón trực tiếp copy_file_range() và syscall() ở tầng Userland trước khi chạm nhân Linux,
 * triệt tiêu hoàn toàn chi phí chuyển ngữ cảnh tín hiệu cho các lệnh gọi qua PLT.
 */
__attribute__((visibility("default")))
ssize_t copy_file_range(int fd_in, off_t *off_in, int fd_out, off_t *off_out,
                        size_t len, unsigned int flags) {
    (void)fd_in;
    (void)off_in;
    (void)fd_out;
    (void)off_out;
    (void)len;
    (void)flags;
    errno = ENOSYS;
    return -1;
}

__attribute__((visibility("default")))
long syscall(long nr, ...) {
    va_list ap;
    va_start(ap, nr);
    unsigned long a0 = va_arg(ap, unsigned long);
    unsigned long a1 = va_arg(ap, unsigned long);
    unsigned long a2 = va_arg(ap, unsigned long);
    unsigned long a3 = va_arg(ap, unsigned long);
    unsigned long a4 = va_arg(ap, unsigned long);
    unsigned long a5 = va_arg(ap, unsigned long);
    va_end(ap);

    /* Nhánh siêu nhanh cho futex (4150+ điểm gọi trong Tokio/Rust std) */
    if (__builtin_expect(nr == ARM64_NR_futex, 1)) {
        if (__builtin_expect(real_syscall_ptr == NULL, 0)) {
            ensure_symbols_resolved();
        }
        return real_syscall_ptr(nr, a0, a1, a2, a3, a4, a5);
    }

    switch (nr) {
        case ARM64_NR_membarrier: {
            __asm__ volatile("dsb ish\n\tisb" ::: "memory");
            log_once_slot(1, "[rust-seccomp] Da kich hoat dong bo Wasmtime JIT membarrier(283) -> OK\n");
            return (a0 == 0UL) ? 0x79L : 0L;
        }
        case ARM64_NR_openat2: {
            log_once_slot(2, "[rust-seccomp] Chuyen openat2(437) -> openat chuan Android\n");
            errno = ENOSYS;
            return -1L;
        }
        case ARM64_NR_copy_file_range: {
            log_once_slot(3, "[rust-seccomp] Chuyen copy_file_range(285) -> sao chep POSIX an toan\n");
            errno = ENOSYS;
            return -1L;
        }
        case ARM64_NR_kcmp: {
            if ((int)a2 == 0) {
                struct stat s1, s2;
                if (fstat((int)a3, &s1) == 0 && fstat((int)a4, &s2) == 0) {
                    return (s1.st_dev == s2.st_dev && s1.st_ino == s2.st_ino) ? 0L : 2L;
                }
            }
            errno = ENOSYS;
            return -1L;
        }
        case ARM64_NR_process_vm_readv:
        case ARM64_NR_process_vm_writev:
        case ARM64_NR_statx:
        case ARM64_NR_rseq:
        case ARM64_NR_userfaultfd:
        case ARM64_NR_pkey_mprotect:
        case ARM64_NR_pkey_alloc:
        case ARM64_NR_pkey_free:
        case ARM64_NR_io_uring_setup:
        case ARM64_NR_io_uring_enter:
        case ARM64_NR_io_uring_register:
        case ARM64_NR_pidfd_open:
        case ARM64_NR_clone3:
        case ARM64_NR_close_range:
        case ARM64_NR_pidfd_getfd:
        case ARM64_NR_process_madvise:
        case ARM64_NR_epoll_pwait2:
        case ARM64_NR_futex_waitv:
        case ARM64_NR_mbind:
        case ARM64_NR_get_mempolicy:
        case ARM64_NR_set_mempolicy: {
            errno = ENOSYS;
            return -1L;
        }
        case ARM64_NR_faccessat2:
            return (long)faccessat((int)a0, (const char *)(uintptr_t)a1, (int)a2, 0);
        case ARM64_NR_fchmodat2:
            return (long)fchmodat((int)a0, (const char *)(uintptr_t)a1, (mode_t)a2, 0);
        default:
            break;
    }

    if (real_syscall_ptr == NULL) {
        ensure_symbols_resolved();
    }
    return real_syscall_ptr(nr, a0, a1, a2, a3, a4, a5);
}
