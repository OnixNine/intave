/* Read the canonical Java viewer using Java's own text-block decoding. */
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '../..');
const rendererFile = path.join(root, 'src/test/java/de/jpx3/intave/check/movement/physics/recording/PtrBranchingVisualizationTest.java');
let template;

function viewerTemplate() {
  if (template) return template;
  const source = fs.readFileSync(rendererFile, 'utf8');
  const blocks = source.match(/private static final String HTML_(?:HEAD|TAIL) = """[\s\S]*?\r?\n\t\t""";/g);
  if (blocks?.length !== 2) throw new Error('Expected the two canonical PTR viewer text blocks');
  const dir = path.join(root, 'build/ptr-highlights/template');
  fs.mkdirSync(dir, {recursive: true});
  const javaFile = path.join(dir, 'PtrViewerTemplate.java');
  const htmlFile = path.join(dir, 'viewer.html');
  fs.writeFileSync(javaFile, `import java.nio.file.*;
class PtrViewerTemplate {
${blocks.join('\n')}
  public static void main(String[] args) throws Exception {
    Files.writeString(Path.of(args[0]), String.join("", HTML_HEAD, HTML_TAIL));
  }
}
`);
  const java = process.env.PTR_JAVA || (process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java');
  const result = spawnSync(java, ['-Dfile.encoding=UTF-8', '--source', '17', javaFile, htmlFile], {encoding: 'utf8'});
  if (result.status !== 0) throw new Error(result.stderr || result.error?.message || 'Cannot compile viewer template');
  template = fs.readFileSync(htmlFile, 'utf8');
  return template;
}

function refreshReport(html) {
  const trace = html.match(/const trace = (.*);\r?\n/);
  const metadata = html.match(/<code>(.*?)<\/code> · protocol (\d+) · server ([^<]+)/);
  const name = html.match(/<title>Physics Branch Visualization · (.*?)<\/title>/);
  const assets = html.match(/const ASSET_VERSION = '([^']+)';/);
  if (!trace || !metadata || !name || !assets) throw new Error('Missing PTR report data or metadata');
  const values = {
    TRACE_DATA: trace[1], RECORDING: metadata[1], RECORDING_NAME: name[1],
    CLIENT_PROTOCOL: metadata[2], SERVER_VERSION: metadata[3], ASSET_VERSION: assets[1]
  };
  return viewerTemplate().replace(/__(TRACE_DATA|RECORDING|RECORDING_NAME|CLIENT_PROTOCOL|SERVER_VERSION|ASSET_VERSION)__/g, (_, key) => values[key]);
}

module.exports = {viewerTemplate, refreshReport, rendererFile};

if (require.main === module) {
  const dir = path.join(root, 'build/reports/ptr-branching');
  let count = 0;
  function refreshDirectory(directory) {
    for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) refreshDirectory(file);
      else if (entry.name.endsWith('.html')) {
        const html = fs.readFileSync(file, 'utf8');
        if (!html.includes('const trace = ')) continue;
        fs.writeFileSync(file, refreshReport(html));
        count++;
      }
    }
  }
  refreshDirectory(dir);
  console.log(`Refreshed ${count} report viewers; preserved their trace data.`);
}
