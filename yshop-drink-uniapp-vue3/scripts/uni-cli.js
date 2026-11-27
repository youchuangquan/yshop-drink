const { spawn } = require('node:child_process')
const path = require('node:path')

process.env.UNI_INPUT_DIR = process.cwd()
const cli = path.join(
  __dirname,
  '../node_modules/@dcloudio/vite-plugin-uni/dist/cli/index.js'
)
const child = spawn(process.execPath, [cli, ...process.argv.slice(2)], {
  stdio: 'inherit',
  env: process.env
})
child.on('exit', (code) => {
  process.exit(code == null ? 1 : code)
})
