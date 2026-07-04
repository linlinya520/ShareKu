#!/usr/bin/env python3
"""Generate a clean buildWebUI function with no JS escaping issues.
Uses JS template literals (backticks) wherever possible to avoid quote hell."""
import os

workspace = '/data/user/0/com.ai.assistance.operit/files/workspace/c4404422-f66a-4219-a496-fac91531c552'
ktfile = os.path.join(workspace, 'app/src/main/java/com/material/localshare/server/LocalShareServer.kt')

# Read current file and find buildWebUI
with open(ktfile, 'r') as f:
    lines = f.readlines()

start_line = None
for i, line in enumerate(lines):
    if 'private fun buildWebUI(): String {' in line:
        start_line = i
        break

if start_line is None:
    print('ERROR')
    exit(1)

prefix = ''.join(lines[:start_line])
print(f'buildWebUI at line {start_line+1}')

# Build a clean, minimal Web UI using Kotlin string concatenation to avoid escaping
# Using all double-quote JS strings eliminates the \\' problem
new_fn = '''    private fun buildWebUI(): String {
        return "<!DOCTYPE html>\\n" +
"<html lang=\\"zh\\">\\n" +
"<head><meta charset=\\"UTF-8\\"><meta name=\\"viewport\\" content=\\"width=device-width,initial-scale=1.0\\">\\n" +
"<title>LocalShare</title>\\n" +
"<style>\\n" +
":root{--bg:#f5f8f6;--card:#fff;--text:#2d3436;--text2:#636e72;--border:#dde4e1;--cyan:#50998b;--cyanL:#e8f3f0;--cyanD:#3d7a6e;--gold:#c9a84c;--goldL:#faf3e0;--goldD:#a68830;--red:#d38c8c;--redL:#fdf0f0;--redD:#b06565;--folder:#50998b;--video:#7b9ec7;--audio:#8bb892;--image:#c9949e;--archive:#c9a06c;--code:#8a95a5;--pdf:#c97d7d;--apk:#6ea89e;--shadow:0 1px 3px rgba(0,0,0,.06),0 1px 2px rgba(0,0,0,.04);--shadowH:0 4px 12px rgba(0,0,0,.08);--radius:14px;--gap:12px}\\n" +
"*{margin:0;padding:0;box-sizing:border-box}\\n" +
"body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--text);min-height:100vh;-webkit-font-smoothing:antialiased}\\n" +
".topbar{display:flex;align-items:center;padding:14px 24px;background:var(--card);border-bottom:1px solid var(--border);position:sticky;top:0;z-index:50;gap:12px;box-shadow:var(--shadow)}\\n" +
".topbar .logo{display:flex;align-items:center;gap:8px;flex-shrink:0}\\n" +
".topbar .logo svg{width:26px;height:26px;color:var(--cyan)}\\n" +
".topbar h1{font-size:17px;font-weight:700;color:var(--text)}\\n" +
".breadcrumb{display:flex;align-items:center;gap:4px;flex:1;overflow-x:auto;font-size:13px;white-space:nowrap;scrollbar-width:none}\\n" +
".breadcrumb a{color:var(--cyan);text-decoration:none;cursor:pointer;padding:4px 8px;border-radius:8px;transition:background .15s;font-weight:500}\\n" +
".breadcrumb a:hover{background:var(--cyanL)}\\n" +
".breadcrumb span{color:var(--text2);padding:0 2px}\\n" +
".topbar .tb-btn{width:34px;height:34px;border-radius:50%;border:none;background:transparent;cursor:pointer;display:flex;align-items:center;justify-content:center;color:var(--text2);transition:all .15s;flex-shrink:0}\\n" +
".topbar .tb-btn:hover{background:var(--bg);color:var(--text)}\\n" +
".topbar .tb-btn svg{width:18px;height:18px}\\n" +
".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:var(--gap);padding:12px 24px 100px}\\n" +
".card{background:var(--card);border-radius:var(--radius);padding:14px 10px 10px;cursor:pointer;transition:all .18s ease;position:relative;box-shadow:var(--shadow);display:flex;flex-direction:column;align-items:center;gap:8px;border-top:3px solid transparent;user-select:none}\\n" +
".card:hover{transform:translateY(-2px);box-shadow:var(--shadowH)}\\n" +
".card:active{transform:scale(.97)}\\n" +
".card.dir{border-top-color:var(--folder)}.card.img{border-top-color:var(--image)}.card.vid{border-top-color:var(--video)}.card.aud{border-top-color:var(--audio)}.card.zip{border-top-color:var(--archive)}.card.pdf{border-top-color:var(--pdf)}.card.apk{border-top-color:var(--apk)}.card.code{border-top-color:var(--code)}\\n" +
".card.selected{background:var(--cyanL);box-shadow:0 0 0 2px var(--cyan);transform:translateY(-2px)}\\n" +
".card .thumbwrap{width:48px;height:48px;display:flex;align-items:center;justify-content:center;position:relative;overflow:hidden;border-radius:8px}\\n" +
".card .thumbwrap img{width:100%;height:100%;object-fit:cover;position:absolute;top:0;left:0;border-radius:8px}\\n" +
".card .thumbwrap svg{width:36px;height:36px;position:relative;z-index:1}\\n" +
".card.img .thumbwrap svg,.card.vid .thumbwrap svg{width:100%;height:100%;position:absolute;top:0;left:0;z-index:0;opacity:.3}\\n" +
".card.dir .thumbwrap svg{color:var(--folder)}.card.img .thumbwrap svg{color:var(--image)}.card.vid .thumbwrap svg{color:var(--video)}.card.aud .thumbwrap svg{color:var(--audio)}.card.zip .thumbwrap svg{color:var(--archive)}.card.pdf .thumbwrap svg{color:var(--pdf)}.card.apk .thumbwrap svg{color:var(--apk)}.card.code .thumbwrap svg{color:var(--code)}\\n" +
".card .name{font-size:11px;font-weight:500;text-align:center;word-break:break-word;line-height:1.35;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;color:var(--text)}\\n" +
".card .meta{font-size:10px;color:var(--text2)}\\n" +
".card .check{position:absolute;top:10px;right:10px;width:20px;height:20px;border-radius:50%;background:var(--cyan);display:none;align-items:center;justify-content:center;z-index:2}\\n" +
".card .check::after{content:'';display:block;width:5px;height:9px;border:solid #fff;border-width:0 2px 2px 0;transform:rotate(45deg);margin-top:-2px}\\n" +
".card.selected .check{display:flex}\\n" +
".empty{display:flex;flex-direction:column;align-items:center;justify-content:center;padding:80px 20px;color:var(--text2)}\\n" +
".empty svg{width:80px;height:80px;margin-bottom:16px;opacity:.35}\\n" +
".empty p{font-size:15px}\\n" +
".actions{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);background:var(--card);border-radius:40px;padding:8px 16px;display:none;gap:10px;z-index:40;box-shadow:0 4px 24px rgba(0,0,0,.12);border:1px solid var(--border)}\\n" +
".actions.show{display:flex}\\n" +
".btn{padding:10px 18px;border-radius:24px;border:none;font-family:inherit;font-size:13px;font-weight:600;cursor:pointer;transition:all .15s;display:flex;align-items:center;gap:6px;white-space:nowrap}\\n" +
".btn svg{width:16px;height:16px}\\n" +
".btn-cyan{background:var(--cyan);color:#fff}.btn-cyan:hover{background:var(--cyanD)}\\n" +
".btn-danger{background:var(--red);color:#fff}.btn-danger:hover{background:var(--redD)}\\n" +
".btn-ghost{background:transparent;color:var(--text);border:1.5px solid var(--border)}.btn-ghost:hover{background:var(--bg)}\\n" +
".upload-bar{padding:0 24px 8px;display:flex;gap:8px}\\n" +
".upload-bar .btn-up{flex:1;padding:12px;border-radius:var(--radius);background:var(--cyanL);color:var(--cyanD);border:1.5px dashed var(--cyan);font-family:inherit;font-size:13px;font-weight:600;cursor:pointer;text-align:center;transition:all .15s;display:flex;align-items:center;justify-content:center;gap:6px}\\n" +
".upload-bar .btn-up:hover{background:var(--cyan);color:#fff}\\n" +
".upload-bar .btn-up input{display:none}\\n" +
".upload-bar .btn-up svg{width:18px;height:18px}\\n" +
".modal{display:none;position:fixed;inset:0;z-index:100;background:rgba(0,0,0,.85);align-items:center;justify-content:center;padding:20px}\\n" +
".modal.show{display:flex}\\n" +
".modal .close{position:absolute;top:16px;right:20px;width:40px;height:40px;border-radius:50%;background:rgba(255,255,255,.15);color:#fff;border:none;font-size:20px;cursor:pointer;display:flex;align-items:center;justify-content:center;z-index:2;transition:background .15s}\\n" +
".modal .close:hover{background:rgba(255,255,255,.25)}\\n" +
".modal img,.modal video{max-width:92vw;max-height:85vh;border-radius:12px;box-shadow:0 8px 40px rgba(0,0,0,.5)}\\n" +
".clip-panel{display:none;position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);background:var(--card);border-radius:20px;padding:28px;z-index:90;box-shadow:0 8px 40px rgba(0,0,0,.15);border:1px solid var(--border);width:380px;max-width:90vw}\\n" +
".clip-panel.show{display:block}\\n" +
".clip-panel h3{font-size:16px;font-weight:700;margin-bottom:16px;color:var(--text);display:flex;align-items:center;gap:8px}\\n" +
".clip-panel h3 svg{width:20px;height:20px;color:var(--gold)}\\n" +
".clip-panel .clip-row{display:flex;gap:8px;align-items:center;margin-bottom:12px}\\n" +
".clip-panel .clip-row input{flex:1;padding:10px 14px;border:1.5px solid var(--border);border-radius:12px;font-family:inherit;font-size:13px;outline:none;transition:border .15s;background:var(--bg);color:var(--text)}\\n" +
".clip-panel .clip-row input:focus{border-color:var(--cyan)}\\n" +
".clip-panel .clip-row .btn-mini{background:var(--gold);color:#fff;border:none;border-radius:12px;padding:10px 16px;font-size:12px;font-weight:600;cursor:pointer;white-space:nowrap}\\n" +
".clip-panel .phone-text{background:var(--bg);border-radius:12px;padding:12px 16px;font-size:13px;color:var(--text2);min-height:40px;margin-bottom:12px;word-break:break-all;border:1px solid var(--border)}\\n" +
".toast{position:fixed;top:80px;left:50%;transform:translateX(-50%);background:var(--text);color:#fff;padding:10px 24px;border-radius:20px;font-size:13px;font-weight:500;z-index:110;opacity:0;transition:opacity .25s;pointer-events:none}\\n" +
".toast.show{opacity:1}\\n" +
".overlalay{display:none;position:fixed;inset:0;z-index:85;background:rgba(0,0,0,.2)}\\n" +
".overlay.show{display:block}\\n" +
"@media(max-width:600px){.grid{grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:8px;padding:10px 12px 100px}.topbar{padding:10px 16px}.topbar h1{font-size:15px}.breadcrumb{font-size:11px}.actions{bottom:12px}.clip-panel{width:92vw;padding:20px}}\\n" +
"</style></head>\\n" +
"<body>\\n" +
"<div class=\\"topbar\\"><div class=\\"logo\\"><svg viewBox=\\"0 0 24 24\\" fill=\\"none\\" stroke=\\"currentColor\\" stroke-width=\\"2\\"><circle cx=\\"12\\" cy=\\"12\\" r=\\"3\\"/><path d=\\"M12 1v4M12 19v4M4.22 4.22l2.83 2.83M16.95 16.95l2.83 2.83M1 12h4M19 12h4M4.22 19.78l2.83-2.83M16.95 7.05l2.83-2.83\\"/></svg><h1>LocalShare</h1></div><div id=\\"crumb\\" class=\\"breadcrumb\\"></div></div>\\n" +
"<div class=\\"upload-bar\\" id=\\"uploadArea\\"></div>\\n" +
"<div class=\\"grid\\" id=\\"grid\\"></div>\\n" +
"<div class=\\"empty\\" id=\\"empty\\"><svg viewBox=\\"0 0 24 24\\" fill=\\"currentColor\\"><path d=\\"M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z\\"/></svg><p>\\u76ee\\u5f55\\u4e3a\\u7a7a</p></div>\\n" +
"<div class=\\"actions\\" id=\\"actions\\"><button class=\\"btn btn-cyan\\" onclick=\\"downloadSelected()\\"><svg viewBox=\\"0 0 24 24\\" fill=\\"currentColor\\"><path d=\\"M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z\\"/></svg>\\u4e0b\\u8f7d ZIP</button><button class=\\"btn btn-danger\\" id=\\"deleteBtn\\" onclick=\\"deleteSelected()\\"><svg viewBox=\\"0 0 24 24\\" fill=\\"currentColor\\"><path d=\\"M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z\\"/></svg>\\u5220\\u9664</button><button class=\\"btn btn-ghost\\" onclick=\\"clearSelection()\\">\\u53d6\\u6d88</button></div>\\n" +
"<div class=\\"modal\\" id=\\"previewModal\\" onclick=\\"closePreview(event)\\"><button class=\\"close\\" onclick=\\"closePreview()\\">x</button><div id=\\"previewContent\\"></div></div>\\n" +
"<div class=\\"toast\\" id=\\"toast\\"></div>\\n" +
"<script>\\n" +
// JS: using double quotes everywhere, single quotes ONLY for HTML attributes that need them
"var S=[],ws,canUp=false,canDel=false,currentPath='',clickTimer=null,lastClickPath='';\\n" +
"var g=document.getElementById(\\"grid\\"),e=document.getElementById(\\"empty\\"),a=document.getElementById(\\"actions\\"),c=document.getElementById(\\"crumb\\"),u=document.getElementById(\\"uploadArea\\"),t=document.getElementById(\\"toast\\");\\n" +
"function fmtSize(b){if(!b)return\\"0 B\\";if(b<1024)return b+\\" B\\";if(b<1048576)return(b/1024).toFixed(1)+\\" KB\\";if(b<1073741824)return(b/1048576).toFixed(1)+\\" MB\\";return(b/1073741824).toFixed(2)+\\" GB\\"}\\n" +
"function im(n){return /\\.(png|jpe?g|gif|webp|svg|bmp)$/i.test(n)}\\n" +
"function vi(n){return /\\.(mp4|webm|mkv|avi|mov)$/i.test(n)}\\n" +
"function au(n){return /\\.(mp3|wav|ogg|flac|aac|m4a)$/i.test(n)}\\n" +
"function ic(f){\\n" +
"if(f.isDirectory)return{svg:\\"<svg viewBox='0 0 24 24' fill='currentColor'><path d='M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg>\\",cls:\\"dir\\"};\\n" +
"var n=f.name.toLowerCase();\\n" +
"if(vi(n))return{svg:\\"<svg viewBox='0 0 24 24' fill='currentColor'><path d='M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z'/></svg>\\",cls:\\"vid\\"};\\n" +
"if(au(n))return{svg:\\"<svg viewBox='0 0 24 24' fill='currentColor'><path d='M12 3v9.28c-.47-.17-.97-.28-1.5-.28C8.01 12 6 14.01 6 16.5S8.01 21 10.5 21c2.31 0 4.2-1.75 4.45-4H15V6h4V3h-7z'/></svg>\\",cls:\\"aud\\"};\\n" +
"if(im(n))return{svg:\\"<svg viewBox='0 0 24 24' fill='currentColor'><path d='M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z'/></svg>\\",cls:\\"img\\"};\\n" +
"if(n.endsWith(\\".pdf\\"))return{svg:\\"<svg viewBox='0 0 24 24' fill='currentColor'><path d='M20.56 15.95L20.56 15.95z'/></svg>\\",cls:\\"pdf\\"};\\n" +
"if(n.match(/\\.(zip|rar|7z|tar|gz)$/))return{svg:\\"<svg viewBox='0 0 24 24' fill='currentColor'><path d='M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z'/></svg>\\",cls:\\"zip\\"};\\n" +
"if(n.endsWith(\\".apk\\"))return{svg:\\"<svg viewBox='0 0 24 24' fill='currentColor'><path d='M6 18c0 .55.45 1 1 1h1v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h2v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h1c.55 0 1-.45 1-1V8H6v10zM3.5 8C2.67 8 2 8.67 2 9.5v7c0 .83.67 1.5 1.5 1.5S5 17.33 5 16.5v-7C5 8.67 4.33 8 3.5 8zm17 0c-.83 0-1.5.67-1.5 1.5v7c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-7c0-.83-.67-1.5-1.5-1.5zm-4.97-5.84l1.3-1.3c.2-.2.2-.51 0-.71-.2-.2-.51-.2-.71 0l-1.48 1.48C13.85 1.23 12.95 1 12 1c-.96 0-1.86.23-2.66.63L7.85.15c-.2-.2-.51-.2-.71 0-.2.2-.2.51 0 .71l1.31 1.31C6.97 3.26 6 5.01 6 7h12c0-1.99-.97-3.75-2.47-4.84zM10 5H9V4h1v1zm5 0h-1V4h1v1z'/></svg>\\",cls:\\"apk\\"};\\n" +
"return{svg:\\"<svg viewBox='0 0 24 24' fill='currentColor'><path d='M6 2c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6H6zm7 7V3.5L18.5 9H13z'/></svg>\\",cls:\\"code\\"}}\\n" +
"function isSel(p){return S.some(function(s){return s.path===p})}\\n" +
"function clr(){S=[];a.classList.remove(\\"show\\");var cs=g.querySelectorAll(\\".card.selected\\");for(var i=0;i<cs.length;i++)cs[i].classList.remove(\\"selected\\");lastClickPath=''}\\n" +
"function tog(p,card){var i=S.findIndex(function(s){return s.path===p});if(i>=0){S.splice(i,1);if(card)card.classList.remove(\\"selected\\")}else{S.push({path:p,name:card?card.dataset.name:p,isDir:card?card.dataset.dir:\\"false\\"});if(card)card.classList.add(\\"selected\\")}a.classList.toggle(\\"show\\",S.length>0)}\\n" +
"function toast(m){t.textContent=m;t.classList.add(\\"show\\");setTimeout(function(){t.classList.remove(\\"show\\")},1800)}\\n" +
// Core functions
"async function load(path){\\n" +
"if(path===undefined)path=currentPath;\\n" +
"if(path!==currentPath){history.pushState({p:path},\\'\\',(path?\\"#\\"+path:\\"#\\"));}\\n" +
"currentPath=path;\\n" +
"var parts=path?path.split(\\"/\\").filter(Boolean):[];\\n" +
"var h=\\"<a onclick='load(\\\\\\"\\\\\\")'>\\u6839\\u76ee\\u5f55</a>\\",cur=\\"\\";\\n" +
"for(var i=0;i<parts.length;i++){cur+=(cur?\\"/\\":\\"\\")+parts[i];h+=\\" <span>/</span> <a onclick='load(\\\\\\"\\"+cur.replace(/\"/g,\\""\\")+\\"\\\\\\")'>\\"+parts[i]+\\"</a>\\";}\\n" +
"c.innerHTML=h;clr();\\n" +
"try{var r=await fetch(\\"/api/list?path=\\"+encodeURIComponent(path)),f=await r.json();\\n" +
"if(!Array.isArray(f)){g.innerHTML=\\"\\";e.style.display=\\"flex\\";return}\\n" +
"renderFiles(f);\\n" +
"}catch(x){g.innerHTML=\\"\\";e.style.display=\\"flex\\"}\\n" +
"}\\n" +
"function renderFiles(files){\\n" +
"if(!files.length){g.innerHTML=\\"\\";e.style.display=\\"flex\\";return}\\n" +
"e.style.display=\\"none\\";\\n" +
"files.sort(function(a,b){if(a.isDirectory&&!b.isDirectory)return -1;if(!a.isDirectory&&b.isDirectory)return 1;return a.name.localeCompare(b.name)});\\n" +
"var h=\\"\\";\\n" +
"for(var i=0;i<files.length;i++){\\n" +
"var f=files[i],ico=ic(f),cls=ico.cls,svg=ico.svg;\\n" +
"var imgUrl=im(f.name)?\\"/download?path=\\"+encodeURIComponent(f.path):\\"\\";\\n" +
"var sel=!!isSel(f.path);\\n" +
"h+=\\"<div class='card \\"+cls+(sel?\\" selected\\":\\"\\")+\\"' data-path='\\"+f.path.replace(/'/g,\\"&#39;\\")+\\"' data-dir='\\"+f.isDirectory+\\"' data-name='\\"+f.name.replace(/'/g,\\"&#39;\\")+\\"' onclick='onCard(this,event)'>\\";\\n" +
"h+=\\"<div class='thumbwrap'>\\";\\n" +
"if(imgUrl)h+=\\"<img src='\\"+imgUrl+\\"' loading='lazy' onerror=\\\\\\"this.style.display='none'\\\\\\">\\";\\n" +
"h+=svg+\\"</div>\\";\\n" +
"h+=\\"<div class='name'>\\"+f.name.replace(/</g,\\"&lt;\\")+\\"</div>\\";\\n" +
"h+=\\"<div class='meta'>\\"+(f.isDirectory?\\"\\u6587\\u4ef6\\u5939\\":fmtSize(f.size))+\\"</div>\\";\\n" +
"h+=\\"<div class='check'></div></div>\\";\\n" +
"}\\n" +
"g.innerHTML=h;window.scrollTo(0,0);\\n" +
"}\\n" +
"function onCard(card,ev){\\n" +
"var path=card.dataset.path,isDir=card.dataset.dir==\\"true\\",name=card.dataset.name;\\n" +
"if(ev.ctrlKey||ev.metaKey){tog(path,card);return}\\n" +
"if(lastClickPath===path){\\n" +
"clearTimeout(clickTimer);clickTimer=null;lastClickPath=\\"\\";\\n" +
"if(isDir){load(currentPath?(currentPath+\\"/\\"+name):name)}\\n" +
"else if(im(name)){previewImage(\\"/download?path=\\"+encodeURIComponent(path),name)}\\n" +
"else if(vi(name)){previewVideo(\\"/download?path=\\"+encodeURIComponent(path),name)}\\n" +
"else{location.href=\\"/download?path=\\"+encodeURIComponent(path)}\\n" +
"return;\\n" +
"}\\n" +
"clr();tog(path,card);lastClickPath=path;\\n" +
"clickTimer=setTimeout(function(){lastClickPath=\\"\\"},300);\\n" +
"}\\n" +
"function previewImage(url,name){\\n" +
"var pc=document.getElementById(\\"previewContent\\");\\n" +
"pc.innerHTML=\\"<img src='\\"+url+\\"' alt='\\"+name+\\"' style='max-width:92vw;max-height:85vh;border-radius:12px'>\\";\\n" +
"document.getElementById(\\"previewModal\\").classList.add(\\"show\\");\\n" +
"}\\n" +
"function previewVideo(url,name){\\n" +
"var pc=document.getElementById(\\"previewContent\\");\\n" +
"pc.innerHTML=\\"<video src='\\"+url+\\"' controls autoplay style='max-width:92vw;max-height:85vh;border-radius:12px'>\\u60a8\\u7684\\u6d4f\\u89c8\\u5668\\u4e0d\\u652f\\u6301\\u89c6\\u9891\\u64ad\\u653e</video>\\";\\n" +
"document.getElementById(\\"previewModal\\").classList.add(\\"show\\");\\n" +
"}\\n" +
"function closePreview(ev){if(ev&&ev.target!==ev.currentTarget)return;document.getElementById(\\"previewModal\\").classList.remove(\\"show\\");document.getElementById(\\"previewContent\\").innerHTML=\\"\\"}\\n" +
"function downloadSelected(){if(!S.length)return;location.href=\\"/api/zip?paths=\\"+S.map(function(s){return encodeURIComponent(s.path)}).join(\\"&paths=\\")}\\n" +
"async function deleteSelected(){\\n" +
"if(!S.length)return;if(!confirm(\\"\\u786e\\u5b9a\\u8981\\u5220\\u9664 \\"+S.length+\\" \\u4e2a\\u6587\\u4ef6/\\u6587\\u4ef6\\u5939\\u5417\\uff1f\\"))return;\\n" +
"for(var i=0;i<S.length;i++){try{await fetch(\\"/api/delete?path=\\"+encodeURIComponent(S[i].path),{method:\\"DELETE\\"})}catch(x){}}\\n" +
"clr();load(currentPath);\\n" +
"}\\n" +
"function doUpload(){\\n" +
"var fi=document.getElementById(\\"fi\\");if(!fi||!fi.files.length)return;\\n" +
"var total=fi.files.length,done=0;\\n" +
"function upOne(file){return new Promise(function(res,rej){\\n" +
"var xhr=new XMLHttpRequest();xhr.open(\\"POST\\",\\"/api/upload\\");\\n" +
"xhr.onload=function(){done++;if(xhr.status===200)res();else rej()};\\n" +
"xhr.onerror=function(){done++;rej()};xhr.send(file);})}\\n" +
"(async function(){for(var i=0;i<total;i++){try{await upOne(fi.files[i])}catch(e){}}\\n" +
"toast(\\"\\u4e0a\\u4f20\\u5b8c\\u6210\\");fi.value=\\"\\";load(currentPath);})();\\n" +
"}\\n" +
"function sendClip(){var v=document.getElementById(\\"clipIn\\").value.trim();if(!v||!ws||ws.readyState!==1)return;ws.send(\\"clipboard:\\"+v);document.getElementById(\\"clipIn\\").value=\\"\\";toast(\\"\\u5df2\\u53d1\\u9001\\")}\\n" +
"function getClip(){if(ws&&ws.readyState===1)ws.send(\\"get_clipboard\\")}\\n" +
"function connectWS(){try{ws=new WebSocket((location.protocol==\\"https:\\"?\\"wss\\":\\"ws\\")+\\"://\\"+location.host+\\"/ws\\");ws.onmessage=function(m){if(m.data.startsWith(\\"clipboard_data:\\"))document.getElementById(\\"phoneClip\\").textContent=m.data.slice(15)||\\"(\\u7a7a)\\";};ws.onclose=function(){setTimeout(connectWS,3000)}}catch(x){}}\\n" +
// Init
"(async function(){try{var r=await fetch(\\"/api/status\\"),s=await r.json();\\n" +
"canUp=s.allowUpload;canDel=s.allowDelete;\\n" +
"if(canUp)u.innerHTML='<label class=\\\\\\"btn-up\\\\\\"><svg viewBox=\\\\\\"0 0 24 24\\\\\\" fill=\\\\\\"currentColor\\\\\\"><path d=\\\\\\"M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\\\\\\"/></svg>\\u4e0a\\u4f20\\u6587\\u4ef6<input type=\\\\\\"file\\\\\\" id=\\\\\\"fi\\\\\\" multiple onchange=\\\\\\"doUpload()\\\\\\"></label>';\\n" +
"if(canDel)document.getElementById(\\"deleteBtn\\").style.display=\\"\\";else document.getElementById(\\"deleteBtn\\").style.display=\\"none\\";\\n" +
"}catch(x){}load(\\"\\");connectWS();})();\\n" +
"window.addEventListener(\\"popstate\\",function(e){if(e.state&&e.state.p!==undefined&&e.state.p!==currentPath)load(e.state.p)});\\n" +
"(function(){var h=location.hash.slice(1);if(h){currentPath=\\"\\";load(decodeURIComponent(h))}})();\\n" +
"document.addEventListener(\\"keydown\\",function(e){if(e.key===\\"Escape\\"){if(document.getElementById(\\"previewModal\\").classList.contains(\\"show\\"))closePreview();else clr()}});\\n" +
"</script>\\n" +
"</body></html>"
    }
'''

# Write the file
with open(ktfile, 'w') as f:
    f.write(prefix)
    f.write(new_fn)

print('Done writing clean buildWebUI')