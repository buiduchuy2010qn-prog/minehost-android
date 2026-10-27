const fs = require('node:fs/promises')
const path = require('node:path')
const net = require('node:net')
const { spawn } = require('node:child_process')
const { once } = require('node:events')
const crypto = require('node:crypto')
const assert = require('node:assert/strict')
const mineflayer = require('mineflayer')
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const version = process.argv[2]
const root = process.env.RUNNER_TEMP
const reports = path.resolve('gameplay-reports', version)
const fixtures = path.resolve('gameplay-fixtures', version)
if (process.env.MINEHOST_TEST_EULA_ACCEPTED !== 'true') throw Error('Explicit EULA consent is required for temporary test servers')
if (!root || !['1.20.4', '1.21.1', '1.21.4'].includes(version)) throw Error('Unexpected runner or version')

class Rcon {
  constructor(socket) {
    this.socket = socket; this.buffer = Buffer.alloc(0); this.next = 1; this.pending = new Map()
    socket.on('data', data => {
      this.buffer = Buffer.concat([this.buffer, data])
      while (this.buffer.length >= 4 && this.buffer.length >= this.buffer.readInt32LE(0) + 4) {
        const length = this.buffer.readInt32LE(0)
        const packet = this.buffer.subarray(4, 4 + length); this.buffer = this.buffer.subarray(4 + length)
        const id = packet.readInt32LE(0), type = packet.readInt32LE(4)
        if (type === 0 && this.pending.get(id)?.auth) continue
        const pending = this.pending.get(id)
        if (pending) { this.pending.delete(id); clearTimeout(pending.timer); pending.resolve(packet.subarray(8, -2).toString('utf8')) }
      }
    })
    socket.on('error', error => { for (const p of this.pending.values()) p.reject(error) })
  }
  send(text, type = 2) {
    const id = this.next++, payload = Buffer.from(text)
    const packet = Buffer.alloc(payload.length + 14)
    packet.writeInt32LE(payload.length + 10, 0); packet.writeInt32LE(id, 4); packet.writeInt32LE(type, 8); payload.copy(packet, 12)
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(Error('RCON timeout: ' + text)) }, 15000)
      this.pending.set(id, { resolve, reject, timer, auth: type === 3 }); this.socket.write(packet)
    })
  }
  async expect(command, label) {
    await this.send('scoreboard objectives add mh_test dummy')
    await this.send('scoreboard players set #assert mh_test 0')
    const response = await this.send(command + ' run scoreboard players set #assert mh_test 1')
    const result = await this.send('scoreboard players get #assert mh_test')
    assert(result.includes('has 1 '), label + ': ' + response + ' / ' + result)
  }
  close() { this.socket.destroy() }
}
async function connectRcon() {
  const socket = net.createConnection({ host: '127.0.0.1', port: 25575 })
  await once(socket, 'connect')
  const rcon = new Rcon(socket)
  await rcon.send('minehost-local-test', 3)
  return rcon
}
async function waitFor(predicate, label, timeout = 90000) {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) { if (await predicate()) return; await sleep(200) }
  throw Error('Timed out: ' + label)
}
async function startServer(dir, jar, label) {
  const proc = spawn('java', ['-Xms256M', '-Xmx1024M', '-jar', jar, 'nogui'], { cwd: dir, stdio: ['pipe', 'pipe', 'pipe'] })
  let log = '', exited = false
  proc.stdout.on('data', b => { log += b.toString() }); proc.stderr.on('data', b => { log += b.toString() })
  proc.on('exit', () => { exited = true })
  try {
    await waitFor(() => { if (exited) throw Error('Server exited: ' + log.slice(-4000)); return /Done \(/.test(log) }, 'server startup', 180000)
    const rcon = await connectRcon()
    await waitFor(async () => (await rcon.send('data get storage minehost:setup initialized')).includes('1b'), 'datapack initialization')
    assert(!/Failed to load function|Failed to parse|Couldn't load tag|Couldn't load function|Unknown function/.test(log), 'Datapack parse/load error: ' + log)
    return { proc, rcon, getLog: () => log, label }
  } catch (error) {
    proc.kill('SIGTERM'); await fs.writeFile(path.join(reports, label + '.log'), log); throw error
  }
}
async function stopServer(server) {
  server.rcon.close()
  server.proc.stdin.write('stop\n')
  await Promise.race([once(server.proc, 'exit'), sleep(45000).then(() => { if (server.proc.exitCode === null) { server.proc.kill('SIGKILL'); throw Error('Server failed to stop gracefully') } })])
  const log = server.getLog()
  await fs.writeFile(path.join(reports, server.label + '.log'), log)
  assert(!/Failed to load function|Failed to parse|Couldn't load tag|Couldn't load function/.test(log), 'Datapack error in server log')
}
async function connectBot() {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'MineHostTest', auth: 'offline', version, checkTimeoutInterval: 60000 })
  bot.on('error', error => console.error('Bot error:', error.message))
  await Promise.race([once(bot, 'spawn'), sleep(60000).then(() => { throw Error('Player failed to spawn') })])
  await bot.waitForTicks(10)
  return bot
}
async function testMode(mode, jar) {
  const dir = path.join(root, 'minehost-gameplay-' + version + '-' + mode)
  await fs.cp(path.join(fixtures, mode), dir, { recursive: true, errorOnExist: true })
  await fs.writeFile(path.join(dir, 'eula.txt'), '# User explicitly accepted for this temporary CI server on 2026-10-27\neula=true\n')
  let server, bot
  try {
    server = await startServer(dir, jar, mode + '-first-start')
    bot = await connectBot()
    const r = server.rcon
    await r.expect('execute if entity @a[name=MineHostTest,tag=mh_joined]', 'joined')
    await r.expect('execute if block 10 0 10 minecraft:air', 'void_terrain')
    await r.expect('execute as MineHostTest at @s if entity @s[x=-1,y=64,z=-1,dx=2,dy=10,dz=2]', 'safe_spawn')
    if (mode === 'oneblock') {
      await r.send('gamemode creative MineHostTest')
      await r.send('tp MineHostTest 0.5 65 0.5'); await bot.waitForTicks(5)
      const block = bot.blockAt(new (require('vec3').Vec3)(0, 64, 0))
      assert(block && block.name === 'grass_block', 'initial OneBlock is available')
      await bot.dig(block, true)
      await waitFor(async () => (await r.send('scoreboard players get #blocks mh_blocks')).includes('has 1 '), 'actual block mining')
      await r.expect('execute if block 0 64 0 minecraft:dirt', 'first_regeneration')
      await r.send('scoreboard players set #blocks mh_blocks 49'); await r.send('scoreboard players set #cycle mh_cycle 0')
      await r.send('setblock 0 64 0 air'); await sleep(200)
      await r.expect('execute if block 0 64 0 minecraft:iron_ore', 'second_phase')
      await r.send('scoreboard players set #blocks mh_blocks 149'); await r.send('scoreboard players set #cycle mh_cycle 0')
      await r.send('setblock 0 64 0 air'); await sleep(200)
      await r.expect('execute if block 0 64 0 minecraft:diamond_ore', 'third_phase')
      await r.expect('execute if score #blocks mh_blocks matches 150', 'progress_persisted')
      await r.expect('execute if data block 3 65 0 Items[{id:"minecraft:water_bucket"}]', 'starter_items')
      await bot.waitForTicks(30)
      assert(bot.inventory.items().some(item => item.name === 'golden_apple'), '25-block bonus is collected by real player')
    } else if (mode === 'parkour') {
      await r.send('tp MineHostTest 0.5 72 17.5'); await bot.waitForTicks(10)
      await r.expect('execute if score MineHostTest mh_checkpoint matches 5', 'checkpoint_five')
      await r.send('tp MineHostTest 0.5 20 17.5'); await bot.waitForTicks(10)
      await r.expect('execute as MineHostTest at @s if entity @s[x=-1,y=72,z=16,dx=2,dy=2,dz=2]', 'checkpoint_rescue')
      await r.send('tp MineHostTest 0.5 76 77.5'); await bot.waitForTicks(10)
      await r.expect('execute if score MineHostTest mh_checkpoint matches 25', 'checkpoint_twentyfive')
      await r.send('tp MineHostTest 0.5 76 80.5'); await bot.waitForTicks(10)
      await r.expect('execute if score MineHostTest mh_won matches 1', 'finish_once')
      assert.equal(bot.inventory.items().filter(item => item.name === 'diamond').reduce((n, item) => n + item.count, 0), 1)
      await bot.waitForTicks(20)
      assert.equal(bot.inventory.items().filter(item => item.name === 'diamond').reduce((n, item) => n + item.count, 0), 1, 'finish does not duplicate rewards')
    } else {
      await r.expect('execute if block 0 64 0 minecraft:grass_block', 'island_floor')
      await r.expect('execute if data block -2 65 0 Items[{id:"minecraft:lava_bucket"}]', 'lava_bucket_available')
      await r.send('tp MineHostTest 0.5 20 0.5'); await bot.waitForTicks(10)
      await r.expect('execute as MineHostTest at @s if entity @s[x=-1,y=65,z=-1,dx=2,dy=2,dz=2]', 'island_rescue')
      if (mode === 'lucky_island') {
        await r.send('forceload add 24 0'); await sleep(200)
        await r.expect('execute if data block 24 65 0 Items[{id:"minecraft:diamond"}]', 'treasure_chest')
      }
    }
    // Build at a location touched by init, then reload and restart. It must never be regenerated.
    const marker = mode === 'parkour' ? '0 70 0' : mode === 'oneblock' ? '3 64 0' : '0 64 0'
    await r.send('setblock ' + marker + ' minecraft:gold_block')
    await r.send('reload'); await bot.waitForTicks(20)
    await r.expect('execute if block ' + marker + ' minecraft:gold_block', 'reload_keeps_build')
    await r.send('save-all flush')
    bot.quit(); bot = null
    await stopServer(server); server = null
    server = await startServer(dir, jar, mode + '-restart')
    await server.rcon.expect('execute if block ' + marker + ' minecraft:gold_block', 'restart_keeps_build')
    if (mode === 'oneblock') await server.rcon.expect('execute if score #blocks mh_blocks matches 150', 'restart_keeps_progress')
    if (mode === 'parkour') await server.rcon.expect('execute if score MineHostTest mh_checkpoint matches 25', 'restart_keeps_checkpoint')
    await stopServer(server); server = null
    return { version, mode, result: 'passed', checks: 'real player join/gameplay, void terrain, starter items, reload and restart preservation' }
  } finally {
    if (bot) bot.quit()
    if (server) await stopServer(server)
  }
}
async function main() {
  await fs.mkdir(reports, { recursive: true })
  const manifest = await (await fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')).json()
  const entry = manifest.versions.find(v => v.id === version)
  assert(entry, 'Version in official Mojang manifest')
  const metadata = await (await fetch(entry.url)).json()
  const download = metadata.downloads.server
  const response = await fetch(download.url); assert(response.ok, 'Official server download')
  const bytes = Buffer.from(await response.arrayBuffer())
  assert.equal(bytes.length, download.size)
  assert.equal(crypto.createHash('sha1').update(bytes).digest('hex'), download.sha1)
  const jar = path.join(root, 'minehost-official-server-' + version + '.jar')
  await fs.writeFile(jar, bytes)
  const results = []
  for (const mode of ['oneblock', 'skyblock', 'parkour', 'lucky_island']) {
    console.log('Checking', version, mode)
    results.push(await testMode(mode, jar))
    await fs.writeFile(path.join(reports, 'results.json'), JSON.stringify(results, null, 2))
  }
  console.log(JSON.stringify(results, null, 2))
}
main().then(() => process.exit(0)).catch(error => { console.error(error); process.exit(1) })
