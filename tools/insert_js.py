#!/usr/bin/env python3
"""Insert remaining JS functions into buildWebUI"""
import re

workspace = '/data/user/0/com.ai.assistance.operit/files/workspace/c4404422-f66a-4219-a496-fac91531c552'
ktfile = f'{workspace}/app/src/main/java/com/material/localshare/server/LocalShareServer.kt'

with open(ktfile, 'r') as f:
    content = f.read()

# Find toast function + closing """
toast_marker = 'function toast(m){t.textContent=m;t.classList.add("show");setTimeout(function(){t.classList.remove("show")},1800)}'
idx = content.find(toast_marker)
if idx < 0:
    print('ERROR: toast not found')
    exit(1)
idx += len(toast_marker)

close_marker = '""".trimIndent()'
close_idx = content.find(close_marker, idx)
if close_idx < 0:
    print('ERROR: close marker not found')
    exit(1)

print(f'Inserting at {idx}, closing at {close_idx}')

new_js = r'''
// Core: load files
async function load(path){
if(path===undefined)path=currentPath;
if(path!==currentPath){history.pushState({p:path},"",(path?"#"+path:"#"));}
currentPath=path;
var parts=path?path.split("/").filter(Boolean):[];
var h="<a onclick='load(\"\")'>root</a>",cur="";
for(var i=0;i<parts.length;i++){cur+=(cur?"/":"")+parts[i];h+=" <span>/</span> <a onclick='load(\""+cur.replace(/"/g,""")+"\")'>"+parts[i]+"</a>";}
c.innerHTML=h;clr();
try{var r=await fetch("/api/list?path="+encodeURIComponent(path)),f=await r.json();
if(!Array.isArray(f)){g.innerHTML="";e.style.display="flex";return}
renderFiles(f);
}catch(x){g.innerHTML="";e.style.display="flex"}
}
function renderFiles(files){
if(!files.length){g.innerHTML="";e.style.display="flex";return}
e.style.display="none";
files.sort(function(a,b){if(a.isDirectory&&!b.isDirectory)return -1;if(!a.isDirectory&&b.isDirectory)return 1;return a.name.localeCompare(b.name)});
var h="";
for(var i=0;i<files.length;i++){
var f=files[i],ico=ic(f),cls=ico.cls,svg=ico.svg;
var imgUrl=im(f.name)?"/download?path="+encodeURIComponent(f.path):"";
var sel=!!isSel(f.path);
h+="<div class='card "+cls+(sel?" selected":"")+"' data-path='"+f.path.replace(/'/g,"&#39;")+"' data-dir='"+f.isDirectory+"' data-name='"+f.name.replace(/'/g,"&#39;")+"' onclick='onCard(this,event)'>";
h+="<div class='thumbwrap'>";
if(imgUrl)h+="<img src='"+imgUrl+"' loading='lazy' onerror='this.style.display=\"none\"'>";
h+=svg+"</div>";
h+="<div class='name'>"+f.name.replace(/</g,"&lt;")+"</div>";
h+="<div class='meta'>"+(f.isDirectory?"dir":fmtSize(f.size))+"</div>";
h+="<div class='check'></div></div>";
}
g.innerHTML=h;window.scrollTo(0,0);
}
function onCard(card,ev){
var path=card.dataset.path,isDir=card.dataset.dir=="true",name=card.dataset.name;
if(ev.ctrlKey||ev.metaKey){tog(path,card);return}
if(lastClickPath===path){
clearTimeout(clickTimer);clickTimer=null;lastClickPath="";
if(isDir){load(currentPath?(currentPath+"/"+name):name)}
else if(im(name)){previewImage("/download?path="+encodeURIComponent(path),name)}
else if(vi(name)){previewVideo("/download?path="+encodeURIComponent(path),name)}
else{location.href="/download?path="+encodeURIComponent(path)}
return;
}
clr();tog(path,card);lastClickPath=path;
clickTimer=setTimeout(function(){lastClickPath=""},300);
}
function previewImage(url,name){
var pc=document.getElementById("previewContent");
pc.innerHTML="<img src='"+url+"' alt='"+name+"' style='max-width:92vw;max-height:85vh;border-radius:12px'>";
document.getElementById("previewModal").classList.add("show");
}
function previewVideo(url,name){
var pc=document.getElementById("previewContent");
pc.innerHTML="<video src='"+url+"' controls autoplay style='max-width:92vw;max-height:85vh;border-radius:12px'></video>";
document.getElementById("previewModal").classList.add("show");
}
function closePreview(ev){if(ev&&ev.target!==ev.currentTarget)return;document.getElementById("previewModal").classList.remove("show");document.getElementById("previewContent").innerHTML=""}
function downloadSelected(){if(!S.length)return;location.href="/api/zip?paths="+S.map(function(s){return encodeURIComponent(s.path)}).join("&paths=")}
async function deleteSelected(){
if(!S.length)return;if(!confirm("Delete "+S.length+" items?"))return;
for(var i=0;i<S.length;i++){try{await fetch("/api/delete?path="+encodeURIComponent(S[i].path),{method:"DELETE"})}catch(x){}}
clr();load(currentPath);
}
function doUpload(){
var fi=document.getElementById("fi");if(!fi||!fi.files.length)return;
var total=fi.files.length;
function upOne(file){return new Promise(function(res,rej){var xhr=new XMLHttpRequest();xhr.open("POST","/api/upload");xhr.onload=function(){if(xhr.status===200)res();else rej()};xhr.onerror=rej;xhr.send(file);})}
(async function(){for(var i=0;i<total;i++){try{await upOne(fi.files[i])}catch(e){}}toast("Uploaded");fi.value="";load(currentPath);})();
}
function connectWS(){try{ws=new WebSocket((location.protocol=="https:"?"wss":"ws")+"://"+location.host+"/ws");ws.onmessage=function(m){};ws.onclose=function(){setTimeout(connectWS,3000)}}catch(x){}}
(function(){
var initPath=location.hash.slice(1)||"";
(async function(){try{var r=await fetch("/api/status"),s=await r.json();
canUp=s.allowUpload;canDel=s.allowDelete;
if(canUp)u.innerHTML='<label class="btn-up"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>Upload<input type="file" id="fi" multiple onchange="doUpload()"></label>';
if(!canDel)document.getElementById("deleteBtn").style.display="none";
}catch(x){}load(initPath);connectWS();})();
})();
window.addEventListener("popstate",function(e){if(e.state&&e.state.p!==undefined&&e.state.p!==currentPath)load(e.state.p)});
document.addEventListener("keydown",function(e){if(e.key==="Escape"){if(document.getElementById("previewModal").classList.contains("show"))closePreview();else clr()}});
'''

before = content[:idx]
after = content[close_idx:]
new_content = before + new_js + after

with open(ktfile, 'w') as f:
    f.write(new_content)
print('Done. New file length:', len(new_content))