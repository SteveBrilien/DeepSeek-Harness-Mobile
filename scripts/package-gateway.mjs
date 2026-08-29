import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const cwd = path.join(root, 'packages', 'mobile-gateway')
const destination = path.join(root, 'artifacts', 'gateway')
await mkdir(destination, { recursive: true })

const pnpmCli = process.env.npm_execpath
if (!pnpmCli) throw new Error('run this script through pnpm')
const child = spawn(process.execPath, [pnpmCli, 'pack', '--pack-destination', destination], { cwd, stdio: 'inherit' })
const code = await new Promise((resolve) => child.once('exit', resolve))
if (code !== 0) process.exit(Number(code || 1))
