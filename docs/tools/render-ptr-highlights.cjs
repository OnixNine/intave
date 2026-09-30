/* Export the existing PTR HTML reports without modifying recordings or physics. */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { pathToFileURL } = require('node:url');
const { spawnSync } = require('node:child_process');
const { refreshReport, rendererFile } = require('./ptr-viewer-template.cjs');
const { planBranchTour } = require('./ptr-branch-tour.cjs');

const root = path.resolve(__dirname, '../..');
const work = path.join(root, 'build/ptr-highlights');
const output = path.join(root, 'docs/assets/ptr-highlights');
const cache = path.join(work, 'asset-cache');
const items = JSON.parse(fs.readFileSync(path.join(__dirname, 'ptr-highlights.json'), 'utf8'));
const preview = process.argv.includes('--preview');
const encodeOnly = process.argv.includes('--encode-only');
const only = process.argv.find(arg => arg.startsWith('--only='))?.slice(7);
const bundled = path.join(process.env.USERPROFILE || process.env.HOME || '', '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules');
const systemChrome = path.join(process.env.ProgramFiles || 'C:/Program Files', 'Google/Chrome/Application/chrome.exe');
const chrome = process.env.PTR_CHROME || (fs.existsSync(systemChrome) ? systemChrome : undefined);
const hash = data => crypto.createHash('sha256').update(data).digest('hex');
const escape = text => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');

function loadReport(item) {
  const file = path.join(root, 'build/reports/ptr-branching', item.report);
  const html = refreshReport(fs.readFileSync(file, 'utf8'));
  const start = html.indexOf('const trace = ') + 14;
  if (start < 14) throw new Error(`Missing trace in ${file}`);
  const end = html.indexOf(';', start);
  const all = JSON.parse(html.slice(start, end));
  const trace = all.filter(t => t.tick >= item.startTick && t.tick <= item.endTick);
  if (!trace.length) throw new Error(`Empty range for ${item.id}`);
  const recording = path.join(root, 'src/test/resources/physics_test_runs', item.report.replace(/\.html$/, '.ptr'));
  const version = html.match(/protocol (\d+) · server ([^<]+)/);
  const tour = planBranchTour(trace, item);
  return { html: html.slice(0, start) + JSON.stringify(trace) + html.slice(end), trace, tour,
    metadata: { ...item, sourceReport: path.relative(root, file).replaceAll('\\', '/'),
      recording: path.relative(root, recording).replaceAll('\\', '/'),
      recordingSha256: hash(fs.readFileSync(recording)), reportSha256: hash(fs.readFileSync(file)),
      rendererSha256: hash(fs.readFileSync(rendererFile)), movementPossibilities: 'all-recorded-candidates',
      tourRendererSha256: hash(fs.readFileSync(path.join(__dirname, 'ptr-branch-tour-view.js'))),
      branchTour: tour, backgroundColor: '#0A0A0A', focusedPlayerVisibility: 'camera-orbit', cameraMode: 'cinematic-follow',
      reportModified: fs.statSync(file).mtime.toISOString(), clientProtocol: Number(version[1]), serverVersion: version[2],
      firstTick: trace[0].tick, lastTick: trace.at(-1).tick, replaySamples: trace.length,
      maxSelectedLoss: Math.max(...trace.map(t => t.loss)),
      maxRecordedDisplacement: Math.max(...trace.map(t => Math.hypot(t.actualX, t.actualY, t.actualZ))) } };
}

function exportHook(item, source) {
  const view = fs.readFileSync(path.join(__dirname, 'ptr-branch-tour-view.js'), 'utf8');
  return `const exportConfig = ${JSON.stringify(item)};\nconst exportTour = ${JSON.stringify(source.tour)};\n` + view;
}
function prepare(item, source) {
  let html = source.html;
  const style = `<style>
    :root {color-scheme:dark; font-family:'Segoe UI',Arial,sans-serif!important;--bg:#0a0a0a;--surface:#0a0a0a}
    * {font-family:'Segoe UI',Arial,sans-serif} body{width:960px;height:600px;overflow:hidden;background:#0a0a0a}
    main{position:absolute!important;top:0!important;left:0!important;width:558px!important;margin:0!important;padding:0!important}
    main>h1,main>.subtitle,.toolbar,.browser-sidebar,.page-header,.page-content,.window-header,.world-overlay,main>.explainer{display:none!important}
    .possibility-browser{display:block!important;border:0!important;border-radius:0!important;min-height:0!important}
    .browser-page{border:0!important}.world-viewport{height:600px!important;border:0!important}.world-viewport canvas{height:600px!important}
    .world-viewport .legend,.browser-page>.legend{display:none!important}
    .tree-panel{position:absolute;left:558px;right:0;top:0;height:600px;border-left:1px solid #2b3542;background:#0a0a0a;padding:0 18px}
    #export-tree{width:366px;height:600px;display:block}
  </style>`;
  html = html.replace('</head>', style + '</head>');
  // Material names are not always texture filenames. These aliases affect export appearance only.
  html = html.replace('const textureAliases = Object.freeze({', `const textureAliases = Object.freeze({
    PISTON: 'piston_side', PISTON_HEAD: 'piston_top', STICKY_PISTON: 'piston_top_sticky', MOVING_PISTON: 'piston_side', STONE_BUTTON: 'stone',
    JUNGLE_FENCE: 'jungle_planks', GRAY_CARPET: 'gray_wool', BLACK_CARPET: 'black_wool',`);
  // Vanilla water textures are grayscale; apply a water tint and reduce stacked-face opacity.
  html = html.replace('if (foliageBlocks.has(material)) {', `if (material === 'WATER' || material === 'BUBBLE_COLUMN') {
    sideTint = topTint = bottomTint = 0x379ee0;
  }
  if (foliageBlocks.has(material)) {`);
  html = html.replace('WATER: .56', 'WATER: .12').replace('BUBBLE_COLUMN: .56', 'BUBBLE_COLUMN: .15');
  const overlay = '<aside class="tree-panel"><svg id="export-tree" viewBox="0 0 366 600" aria-label="Movement branch tree"></svg></aside>';
  html = html.replace('<body>', '<body>' + overlay);
  const lastScript = html.lastIndexOf('</script>');
  html = html.slice(0, lastScript) + exportHook(item, source) + html.slice(lastScript);
  const file = path.join(work, item.id + '.html');
  fs.writeFileSync(file, html);
  return file;
}

const frameCount = (item, source) => Math.round(source.tour.durationSeconds * 20);

function encode(item,source,frames) {
  const framesDir=path.join(work,item.id);
  const gif=path.join(output,item.id+'.gif');
  const outputFrames=Math.round(frames/20*12.5);
  const runFfmpeg=args=>{
    const result=spawnSync(process.env.FFMPEG || 'ffmpeg',['-hide_banner','-loglevel','error','-y',...args],{encoding:'utf8'});
    if(result.status!==0)throw new Error(result.stderr || result.error?.message || 'FFmpeg failed');
  };
  const input=['-framerate','20','-t',String(frames/20),'-i',path.join(framesDir,'%04d.png')];
  const scale='fps=12.5,scale=768:480:flags=lanczos';
  const paletteFile=path.join(framesDir,'palette.rgba');
  runFfmpeg([...input,'-vf',scale+',palettegen=max_colors=96:stats_mode=full','-frames:v','1','-f','rawvideo','-pix_fmt','rgba',paletteFile]);
  // Quantization can merge the flat background with nearby dark shades. Pin its
  // palette entry before mapping pixels so the exported GIF keeps exact #0A0A0A.
  const palette=fs.readFileSync(paletteFile);
  if(palette.length!==16*16*4)throw new Error('Invalid GIF palette');
  let closest=-1, distance=Infinity;
  for(let offset=0;offset<palette.length;offset+=4){
    const delta=(palette[offset]-10)**2+(palette[offset+1]-10)**2+(palette[offset+2]-10)**2;
    if(palette[offset+3]===255 && delta<distance){closest=offset;distance=delta;}
  }
  if(closest<0)throw new Error('GIF palette has no opaque colors');
  palette.fill(10,closest,closest+3);
  fs.writeFileSync(paletteFile,palette);
  runFfmpeg([...input,'-f','rawvideo','-pixel_format','rgba','-video_size','16x16','-i',paletteFile,
    '-frames:v',String(outputFrames),'-filter_complex',`[0:v]${scale}[scaled];[scaled][1:v]paletteuse=dither=none:diff_mode=rectangle`,'-loop','0',gif]);
  fs.copyFileSync(path.join(framesDir,String(Math.floor(frames/2)).padStart(4,'0')+'.png'),path.join(output,item.id+'.png'));
  console.log('Exported '+item.id+' '+(fs.statSync(gif).size/1024/1024).toFixed(2)+' MB');
  return {...source.metadata,textOverlay:true,branchLabels:'recorded-inputs',capturedFrames:frames,captureFps:20,frames:outputFrames,fps:12.5,durationSeconds:outputFrames/12.5,width:768,height:480,bytes:fs.statSync(gif).size,gifSha256:hash(fs.readFileSync(gif)),branchCandidates:source.tour.candidateCount};
}

function saveManifest(manifest) {
  const dest=path.join(output,'manifest.json');
  const previous=fs.existsSync(dest)?JSON.parse(fs.readFileSync(dest,'utf8')):[];
  const merged=only?[...previous.filter(m=>m.id!==only),...manifest].sort((a,b)=>a.id.localeCompare(b.id)):manifest;
  fs.writeFileSync(dest,JSON.stringify(merged,null,2)+'\n');
  const cards=merged.map(item=>`<article><button class="clip" aria-label="Play ${escape(item.title)}" data-name="${escape(item.title)}" data-id="${item.id}" data-version="${item.gifSha256.slice(0,12)}" aria-pressed="false"><img src="${item.id}.png?v=${item.gifSha256.slice(0,12)}" alt="${escape(item.subtitle)}" width="960" height="600"></button></article>`).join('\n');
  const template=fs.readFileSync(path.join(__dirname,'ptr-gallery.html'),'utf8');
  fs.writeFileSync(path.join(output,'index.html'),template.replace('__CARDS__',cards));
}

function selectedItems(onlyId = only) {
  if (onlyId && !items.some(item => item.id === onlyId)) throw new Error(`Unknown clip: ${onlyId}`);
  return items.filter(item => onlyId ? item.id === onlyId : item.enabled !== false);
}

async function main() {
  for (const dir of [work, output, cache]) fs.mkdirSync(dir, { recursive: true });
  if (process.argv.includes('--check')) {
    for (const item of selectedItems()) {
      const source = loadReport(item);
      console.log(`Verified ${item.id}: ${source.trace.length} samples, ${source.tour.candidateCount} candidates`);
    }
    return;
  }
  if(process.argv.includes('--gallery-only')){saveManifest(JSON.parse(fs.readFileSync(path.join(output,'manifest.json'),'utf8')));return;}
  if(encodeOnly){saveManifest(selectedItems().map(item=>{const source=loadReport(item);return encode(item,source,frameCount(item,source));}));return;}
  const { chromium } = require(require.resolve('playwright', { paths: [__dirname, process.env.NODE_PATH || bundled] }));
  const browser = await chromium.launch({ executablePath:chrome,headless:true,args:['--use-angle=swiftshader','--enable-unsafe-swiftshader'] });
  const context = await browser.newContext({ viewport:{width:960,height:600},colorScheme:'dark',deviceScaleFactor:1 });
  const failures = new Set();
  await context.route(/^https?:/, async route => {
    const url = route.request().url();
    if (!['cdn.jsdelivr.net','assets.mcasset.cloud'].includes(new URL(url).hostname)) return route.abort();
    const key = path.join(cache, hash(url));
    if (fs.existsSync(key+'.json') && fs.existsSync(key+'.bin')) {
      const saved = JSON.parse(fs.readFileSync(key+'.json','utf8'));
      return route.fulfill({status:200,contentType:saved.contentType,body:fs.readFileSync(key+'.bin'),headers:{'access-control-allow-origin':'*'}});
    }
    try {
      const response = await route.fetch({timeout:30000});
      if(!response.ok()){failures.add(url+' HTTP '+response.status());return route.fulfill({response});}
      const body=await response.body(); const contentType=response.headers()['content-type'] || 'application/octet-stream';
      fs.writeFileSync(key+'.bin',body);fs.writeFileSync(key+'.json',JSON.stringify({url,contentType}));
      return route.fulfill({status:200,contentType,body,headers:{'access-control-allow-origin':'*'}});
    } catch(error) {failures.add(url+' '+error.message.split('\n')[0]);return route.abort();}
  });
  const manifest=[];
  try {
    for(const item of selectedItems()) {
      const source=loadReport(item);
      const html=prepare(item,source);
      const page=await context.newPage();
      const errors=[];
      page.on('pageerror',e=>errors.push(e.message));
      console.log('Preparing '+item.id);
      await page.goto(pathToFileURL(html).href,{waitUntil:'networkidle',timeout:60000});
      await page.waitForFunction(()=>Boolean(window.ptrExport),{},{timeout:30000});
      if((await page.locator('main').innerText()).trim())throw new Error('Unexpected text outside the branch labels in '+item.id);
      if(failures.size)throw new Error('Asset failures:\n'+[...failures].join('\n'));
      const frames=frameCount(item,source);
      const framesDir=path.join(work,item.id);fs.mkdirSync(framesDir,{recursive:true});
      const tourPreviewTimes = source.tour.visitIds.map((_, index) => source.tour.pauseAt + source.tour.introSeconds + (index + .85) * source.tour.stepSeconds);
      const replayPreviewTimes = [.2, .4, .6, .8].map(part => part * source.tour.replaySeconds)
        .map(time => time >= source.tour.pauseAt ? time + source.tour.tourSeconds : time);
      const captureFrames=preview?[...new Set([0,Math.floor(frames/2),frames-1,...[...tourPreviewTimes,...replayPreviewTimes].map(time=>Math.round(time/source.tour.durationSeconds*(frames-1)))])].sort((a,b)=>a-b):Array.from({length:frames},(_,i)=>i);
      for(const frame of captureFrames) {
        await page.evaluate(({frame,frames})=>window.ptrExport.draw(frame,frames),{frame,frames});
        // Wait for textures first encountered at this sample, then draw once more.
        if(preview || frame%20===0) {
          await page.waitForLoadState('networkidle');
          await page.evaluate(({frame,frames})=>window.ptrExport.draw(frame,frames),{frame,frames});
        }
        await page.screenshot({path:path.join(framesDir,String(frame).padStart(4,'0')+'.png')});
        if(!preview && frame%40===0)console.log(item.id+' '+frame+'/'+frames);
      }
      if(errors.length)throw new Error(item.id+': '+errors.join('; '));
      if(!preview) {
        manifest.push(encode(item,source,frames));
      }
      await page.close();
    }
  } finally {await browser.close();}
  if(failures.size)throw new Error('Asset failures:\n'+[...failures].join('\n'));
  if(!preview) saveManifest(manifest);
}
module.exports = {selectedItems, loadReport};
if (require.main === module) main().catch(error=>{console.error(error);process.exitCode=1;});
