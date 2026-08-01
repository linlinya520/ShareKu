
var ICONS={
 folder:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/></svg>',
 file:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 2c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6H6zm7 7V3.5L18.5 9H13z"/></svg>',
 video:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"/></svg>',
 music:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 3v9.28c-.47-.17-.97-.28-1.5-.28C8.01 12 6 14.01 6 16.5S8.01 21 10.5 21c2.31 0 4.2-1.75 4.45-4H15V6h4V3h-7z"/></svg>',
 picture:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"/></svg>',
 pdf:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M20.56 15.953C19.717 15.341 18.296 15.01 16.336 14.963a16.8 16.8 0 00-2.868-4.434c.527-.959.854-1.683.97-2.146.551-2.18.119-3.775-.47-4.54-.362-.47-.831-.726-1.331-.726-.584 0-1.963.362-2.085 3.719-.037.947.281 2.166.939 3.626-1.107 1.888-2.324 3.707-3.644 5.452-1.078.233-2.119.504-3.009.777-2.566.793-2.785 2.007-2.691 2.661.134.9 1.012 1.531 2.132 1.531.494 0 .999-.123 1.462-.362.769-.392 1.803-1.404 3.078-3.01 2.349-.473 4.784-.76 6.686-.792.499.591 1.231 1.397 1.905 1.902 1.082.818 2.019 1.232 2.792 1.232.773 0 1.366-.41 1.585-1.093.294-.912-.21-2.069-1.227-2.807zm-.455 2.13c-.22-.035-.742-.191-1.644-.868-.138-.104-.27-.216-.396-.334.881.154 1.298.381 1.475.508.252.169.448.412.563.694h.002zm-5.993-3.069c-1.133.074-2.39.217-3.655.414l-.092.015.045-.061c.698-.992 1.385-2.039 1.996-3.032l.028-.048.019.031c.493.868 1.074 1.769 1.682 2.617l.045.06-.068.004zm-1.368-7.067c-.045.162-.106.319-.178.473-.236-.755-.281-1.235-.27-1.512.039-1.063.222-1.629.349-1.907.2.349.499 1.348.099 2.946zM4.286 19.117c-.11.003-.219-.014-.322-.051.148-.147.518-.415 1.387-.687.163-.05.33-.098.504-.15-.508.494-.793.664-.895.714-.207.112-.438.171-.674.174z"/></svg>',
 zip:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M20.54 5.23l-1.39-1.68C18.88 3.21 18.47 3 18 3H6c-.47 0-.88.21-1.16.55L3.46 5.23C3.17 5.57 3 6.02 3 6.5V19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6.5c0-.48-.17-.93-.46-1.27zM12 17.5L6.5 12H10v-2h4v2h3.5L12 17.5zM5.12 5l.81-1h12l.94 1H5.12z"/></svg>',
 apk:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 18c0 .55.45 1 1 1h1v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h2v3.5c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5V19h1c.55 0 1-.45 1-1V8H6v10zM3.5 8C2.67 8 2 8.67 2 9.5v7c0 .83.67 1.5 1.5 1.5S5 17.33 5 16.5v-7C5 8.67 4.33 8 3.5 8zm17 0c-.83 0-1.5.67-1.5 1.5v7c0 .83.67 1.5 1.5 1.5s1.5-.67 1.5-1.5v-7c0-.83-.67-1.5-1.5-1.5zm-4.97-5.84l1.3-1.3c.2-.2.2-.51 0-.71-.2-.2-.51-.2-.71 0l-1.48 1.48C13.85 1.23 12.95 1 12 1c-.96 0-1.86.23-2.66.63L7.85.15c-.2-.2-.51-.2-.71 0-.2.2-.2.51 0 .71l1.31 1.31C6.97 3.26 6 5.01 6 7h12c0-1.99-.97-3.75-2.47-4.84zM10 5H9V4h1v1zm5 0h-1V4h1v1z"/></svg>',
 del:'<svg viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>'
};
function getIcon(f){
 if(f.isDirectory)return[ICONS.folder,'dir'];
 var n=f.name.toLowerCase();
 if(n.match(/\.(mp4|webm|mkv|avi|mov)$/))return[ICONS.video,'vid'];
 if(n.match(/\.(mp3|wav|ogg|flac|aac|m4a)$/))return[ICONS.music,'aud'];
 if(n.match(/\.(png|jpe?g|gif|webp|svg|bmp)$/))return[ICONS.picture,'img'];
 if(n.endsWith('.pdf'))return[ICONS.pdf,'pdf'];
 if(n.match(/\.(zip|rar|7z|tar|gz)$/))return[ICONS.zip,'zip'];
 if(n.endsWith('.apk'))return[ICONS.apk,'apk'];
 if(n.match(/\.(py|js|kt|java|cpp|c|h|ts|rs|go|swift|json|xml|css|html|md|txt|log|sh|yml|yaml|toml)$/))return[ICONS.file,'code'];
 return[ICONS.file,'file'];
}
function fmtSize(b){
 if(!b)return'0 B';
 if(b<1024)return b+' B';if(b<1048576)return(b/1024).toFixed(1)+' KB';
 if(b<1073741824)return(b/1048576).toFixed(1)+' MB';
 return(b/1073741824).toFixed(2)+' GB';
}
var S=[],ws,canUp=false,canDel=false,currentPath='';
var g=document.getElementById('grid'),e=document.getElementById('empty'),a=document.getElementById('actions'),c=document.getElementById('crumb'),u=document.getElementById('uploadArea'),t=document.getElementById('toast');
(async function(){
 try{var r=await fetch('/api/status'),s=await r.json();
  canUp=s.allowUpload;canDel=s.allowDelete;
  if(canUp)u.innerHTML='<label class="btn-up"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>上传文件<input type="file" id="fi" multiple onchange="doUpload()"></label>';
  if(canDel)document.getElementById('deleteBtn').style.display='';else document.getElementById('deleteBtn').style.display='none';
 }catch(x){}
 load('');connectWS();
})();
function connectWS(){
 try{ws=new WebSocket((location.protocol=='https:'?'wss':'ws')+'://'+location.host+'/ws');
  ws.onmessage=function(m){if(m.data.startsWith('clipboard_data:'))document.getElementById('clipIn').value=m.data.slice(15)};
  ws.onclose=function(){setTimeout(connectWS,3000)};
 }catch(x){}
}
async function load(path){
 if(path===undefined)path=currentPath;
 if(path!==currentPath){history.pushState({p:path},'',(path?'#'+path:'#'));}
 currentPath=path;
 var parts=path?path.split('/').filter(Boolean):[];
 var h='<a onclick="load(\'\')">根目录</a>',cur='';
 for(var i=0;i<parts.length;i++){cur+=(cur?'/':'')+parts[i];h+=' <span>/</span> <a onclick="load(\''+cur.replace(/'/g,"\\'")+'\')">'+parts[i]+'</a>';}
 c.innerHTML=h;clearSelection();
 try{var r=await fetch('/api/list?path='+encodeURIComponent(path)),f=await r.json();
  if(!Array.isArray(f)){g.innerHTML='';e.style.display='flex';return}
  renderFiles(f);
 }catch(x){g.innerHTML='';e.style.display='flex'}
}
function renderFiles(files){
 if(!files.length){g.innerHTML='';e.style.display='flex';return}
 e.style.display='none';
 files.sort(function(a,b){if(a.isDirectory&&!b.isDirectory)return -1;if(!a.isDirectory&&b.isDirectory)return 1;return a.name.localeCompare(b.name)});
 var h='';
 for(var i=0;i<files.length;i++){
  var f=files[i],ic=getIcon(f),cls=ic[1],svg=ic[0];
  var sel=S.some(function(s){return s.path===f.path});
  h+='<div class="card '+cls+(sel?' selected':'')+'" data-path="'+f.path.replace(/"/g,'"')+'" data-dir="'+f.isDirectory+'" data-name="'+f.name.replace(/"/g,'"')+'" onclick="onCard(this,event)">';
  h+='<div class="svgwrap">'+svg+'</div>';
  h+='<div class="name">'+f.name.replace(/</g,'&lt;')+'</div>';
  h+='<div class="meta">'+(f.isDirectory?'文件夹':fmtSize(f.size))+'</div>';
  h+='<div class="check"></div></div>';
 }
 g.innerHTML=h;window.scrollTo(0,0);
}
function onCard(card,ev){
 var path=card.dataset.path,isDir=card.dataset.dir=='true',name=card.dataset.name;
 if(ev.ctrlKey||ev.metaKey){toggleSelect(path,card);return}
 // 单击已选中项 → 进入(文件夹)或下载(文件)
 if(isSelected(path)){
  if(isDir){load(currentPath?(currentPath+'/'+name):name)}
  else{location.href='/download?path='+encodeURIComponent(path)}
  return;
 }
 // 单击未选中项 → 添加到选中（不清除其他项，支持同级多选）
 toggleSelect(path,card);
}
function toggleSelect(path,card){
 var idx=S.findIndex(function(s){return s.path===path});
 if(idx>=0){S.splice(idx,1);if(card)card.classList.remove('selected')}
 else{S.push({path:path,name:card?card.dataset.name:path,isDir:card?card.dataset.dir:'false'});if(card)card.classList.add('selected')}
 a.classList.toggle('show',S.length>0);
}
function isSelected(path){return S.some(function(s){return s.path===path})}
function clearSelection(){S=[];a.classList.remove('show');var cs=document.querySelectorAll('.card.selected');for(var i=0;i<cs.length;i++)cs[i].classList.remove('selected')}
function downloadSelected(){if(!S.length)return;location.href='/api/zip?paths='+S.map(function(s){return encodeURIComponent(s.path)}).join('&paths=')}
async function deleteSelected(){
 if(!S.length)return;if(!confirm('确定要删除 '+S.length+' 个文件/文件夹吗？'))return;
 for(var i=0;i<S.length;i++){try{await fetch('/api/delete?path='+encodeURIComponent(S[i].path),{method:'DELETE'})}catch(x){}}
 clearSelection();load(currentPath);
}
async function doUpload(){
 var fi=document.getElementById('fi');if(!fi||!fi.files.length)return;
 var total=fi.files.length,ok=0,fail=0;
 for(var i=0;i<fi.files.length;i++){
  var f=fi.files[i];
  var fd=new FormData();fd.append('file',f);
  try{
   var r=await fetch('/api/upload?name='+encodeURIComponent(f.name),{method:'POST',body:fd});
   if(r.ok){ok++}else{fail++}
  }catch(x){fail++}
 }
 fi.value='';
 showToast('上传完成: '+(total-fail)+'/'+total+' 成功'+(fail>0?' ('+fail+' 失败)':''));
 load(currentPath);
}
function sendClip(){var v=document.getElementById('clipIn').value.trim();if(!v||!ws||ws.readyState!==1)return;ws.send('clipboard:'+v);document.getElementById('clipIn').value='';showToast('已发送')}
function getClip(){if(ws&&ws.readyState===1)ws.send('get_clipboard')}
function downloadMapBat(){
 var webdavUrl = location.protocol + '//' + location.host + '/webdav';
 var script = '@echo off\r\nchcp 65001 >nul\r\necho 正在将 ShareKu 映射为 Z: 盘...\r\necho.\r\n:: 1. 启动 Windows WebDAV 客户端服务\r\nnet start WebClient >nul 2>&1\r\n:: 2. 允许 HTTP 访问 WebDAV\r\nreg add \"HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\WebClient\\Parameters\" /v BasicAuthLevel /t REG_DWORD /d 2 /f >nul 2>&1\r\n:: 3. 清除旧映射\r\nnet use Z: /delete >nul 2>&1\r\n:: 4. 映射网络驱动器\r\nnet use Z: ' + webdavUrl + ' /persistent:no\r\nif %errorlevel%==0 (\r\n echo 成功映射 Z: 盘\r\n explorer Z:\r\n) else (\r\n echo 映射失败\r\n echo 请以管理员身份运行并检查防火墙\r\n)\r\npause\r\n';
 var blob = new Blob([script], {type: 'application/bat'});
 var a = document.createElement('a');
 a.href = URL.createObjectURL(blob);
 a.download = 'ShareKu-MapZ.bat';
 a.click();
 setTimeout(function(){URL.revokeObjectURL(a.href)}, 5000);
 showToast('映射脚本已下载');
}
function showToast(msg){t.textContent=msg;t.classList.add('show');setTimeout(function(){t.classList.remove('show')},1800)}
window.addEventListener('popstate',function(e){if(e.state&&e.state.p!==undefined&&e.state.p!==currentPath)load(e.state.p)});
// 初始加载：从URL hash恢复路径
(function(){var h=location.hash.slice(1);if(h){currentPath='';load(decodeURIComponent(h))}})();
document.addEventListener('keydown',function(e){if(e.key==='Escape')clearSelection()});
