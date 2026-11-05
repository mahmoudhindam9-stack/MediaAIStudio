const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn, execFile } = require('child_process');
const crypto = require('crypto');

const root = __dirname;
const backendDir = path.join(root, 'backend');
const distDir = path.join(root, 'web', 'dist');
const backendEnvPath = path.join(backendDir, '.env');
const PORT = 4173, BACKEND_PORT = 3100, CAMERA_AGENT_PORT = 3101, PHONE_RELAY_PORT = 4174;
const CAMERA_AGENT_TOKEN = crypto.randomBytes(32).toString('hex');
const CAMERA_PAIR_CODE = String(crypto.randomInt(10000000, 99999999));
const phoneSessions = new Map();
let phonePublicUrl = null;
let tunnelProcess = null;
const mime = { '.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.jpg':'image/jpeg','.jpeg':'image/jpeg','.webp':'image/webp','.ico':'image/x-icon','.woff':'font/woff','.woff2':'font/woff2','.mp4':'video/mp4' };

if (!fs.existsSync(path.join(distDir, 'index.html'))) { console.error('Web build is missing. Run Install.bat first.'); process.exit(1); }
if (!fs.existsSync(backendEnvPath)) { console.error('Backend configuration is missing. Run Install.bat first.'); process.exit(1); }
const dotenv = require(path.join(backendDir, 'node_modules', 'dotenv'));
const localEnv = dotenv.parse(fs.readFileSync(backendEnvPath));
const token = localEnv.MEDIA_AI_BACKEND_API_KEY;
if (!token) { console.error('MEDIA_AI_BACKEND_API_KEY is missing from backend/.env'); process.exit(1); }

const logDir = path.join(root, 'logs');
fs.mkdirSync(logDir, { recursive: true });
const backendLog = path.join(logDir, 'backend.log');
const backendOut = fs.createWriteStream(backendLog, { flags: 'a' });
const backendErr = fs.createWriteStream(backendLog, { flags: 'a' });
const cameraAgent = spawn(process.execPath, ['camera-agent.js'], { cwd:root, env:{...process.env,CAMERA_AGENT_PORT:String(CAMERA_AGENT_PORT),CAMERA_AGENT_TOKEN,CAMERA_PAIR_CODE}, stdio:['ignore',backendOut,backendErr], windowsHide:true });
const backend = spawn(process.execPath, ['src/index.js'], { cwd:backendDir, env:{...process.env,PORT:String(BACKEND_PORT)}, stdio:['ignore',backendOut,backendErr], windowsHide:true });

function send(res,status,data){res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data));}
function readBody(req){return new Promise((resolve,reject)=>{let raw='';req.on('data',c=>{raw+=c;if(raw.length>200000)reject(new Error('Request too large'));});req.on('end',()=>{try{resolve(raw?JSON.parse(raw):{});}catch{reject(new Error('Invalid JSON'));}});req.on('error',reject);});}
function getSession(id){const s=phoneSessions.get(id);if(!s||s.expiresAt<Date.now()){phoneSessions.delete(id);return null;}return s;}
function phoneRelayHandler(req,res){
 const u=new URL(req.url||'/', 'http://127.0.0.1');
 if(req.method==='GET'&&u.pathname==='/phone-camera.html'){const file=path.join(root,'web','public','phone-camera.html');return fs.readFile(file,(e,d)=>{if(e){res.writeHead(404);return res.end('Not found');}res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer'});res.end(d);});}
 const match=u.pathname.match(/^\/api\/phone\/([a-f0-9]{32})$/);
 if(!match)return send(res,404,{error:'NOT_FOUND'});
 const session=getSession(match[1]);if(!session)return send(res,410,{error:'PAIRING_EXPIRED'});
 if(req.method==='GET')return send(res,200,{offer:session.offer,answer:session.answer,connected:!!session.answer});
 if(req.method==='POST')return readBody(req).then(body=>{if(body.answer){session.answer=body.answer;session.phoneSeenAt=Date.now();return send(res,200,{ok:true});}return send(res,400,{error:'ANSWER_REQUIRED'});}).catch(e=>send(res,400,{error:e.message}));
 return send(res,405,{error:'METHOD_NOT_ALLOWED'});
}
const relayServer=http.createServer(phoneRelayHandler);
const sessionsCleanup=setInterval(()=>{for(const [id,s] of phoneSessions)if(s.expiresAt<Date.now())phoneSessions.delete(id);},30000);

const server=http.createServer(async(req,res)=>{
 const u=new URL(req.url||'/', 'http://127.0.0.1');
 if(u.pathname==='/api/phone-relay/status'&&req.method==='GET')return send(res,200,{publicUrl:phonePublicUrl});
 if(u.pathname==='/api/phone-relay/session'&&req.method==='POST'){
  const id=crypto.randomBytes(16).toString('hex');phoneSessions.set(id,{offer:null,answer:null,expiresAt:Date.now()+10*60*1000,createdAt:Date.now()});
  return send(res,200,{id,publicUrl:phonePublicUrl,expiresAt:Date.now()+10*60*1000});
 }
 const localMatch=u.pathname.match(/^\/api\/phone-relay\/session\/([a-f0-9]{32})$/);
 if(localMatch){const s=getSession(localMatch[1]);if(!s)return send(res,410,{error:'PAIRING_EXPIRED'});if(req.method==='POST')return readBody(req).then(body=>{if(!body.offer)return send(res,400,{error:'OFFER_REQUIRED'});s.offer=body.offer;return send(res,200,{ok:true});}).catch(e=>send(res,400,{error:e.message}));if(req.method==='GET')return send(res,200,{answer:s.answer,phoneSeenAt:s.phoneSeenAt||null});}
 const cameraPrefix='/api/camera';
 if(u.pathname===cameraPrefix||u.pathname.startsWith(cameraPrefix+'/')){const target=u.pathname.slice(cameraPrefix.length)||'/';const headers={...req.headers,host:'127.0.0.1:'+CAMERA_AGENT_PORT,authorization:'Bearer '+CAMERA_AGENT_TOKEN};delete headers.connection;const upstream=http.request({hostname:'127.0.0.1',port:CAMERA_AGENT_PORT,path:target+u.search,method:req.method,headers},r=>{res.writeHead(r.statusCode||502,r.headers);r.pipe(res);});upstream.on('error',e=>send(res,502,{error:'CAMERA_AGENT_UNAVAILABLE',message:e.message}));req.pipe(upstream);return;}
 const prefix='/api/backend';
 if(u.pathname===prefix||u.pathname.startsWith(prefix+'/')){const target=u.pathname.slice(prefix.length)||'/';const headers={...req.headers,host:'127.0.0.1:'+BACKEND_PORT,authorization:'Bearer '+token};delete headers.connection;const upstream=http.request({hostname:'127.0.0.1',port:BACKEND_PORT,path:target+u.search,method:req.method,headers},r=>{res.writeHead(r.statusCode||502,r.headers);r.pipe(res);});upstream.on('error',e=>send(res,502,{error:'BACKEND_UNAVAILABLE',message:e.message}));req.pipe(upstream);return;}
 let pathname;try{pathname=decodeURIComponent(u.pathname);}catch{res.writeHead(400);return res.end('Bad request');}
 const safe=path.normalize(pathname).replace(/^([/\\]*\.\.[/\\])+/,'' );let file=path.join(distDir,safe);
 if(!file.startsWith(distDir)){res.writeHead(403);return res.end('Forbidden');}
 if(pathname==='/'||!path.extname(file))file=path.join(distDir,'index.html');
 fs.stat(file,(e,st)=>{if(e||!st.isFile())file=path.join(distDir,'index.html');fs.readFile(file,(err,data)=>{if(err){res.writeHead(404);return res.end('Not found');}res.writeHead(200,{'Content-Type':mime[path.extname(file).toLowerCase()]||'application/octet-stream','Cache-Control':'no-cache'});res.end(data);});});
});

function startTunnel(){
 const candidates=['cloudflared','cloudflared.exe'];
 let index=0;
 const tryStart=()=>{if(index>=candidates.length){console.log('Wi-Fi phone camera requires cloudflared. Install it with winget install --id Cloudflare.cloudflared');return;}
  const child=spawn(candidates[index++],['tunnel','--url','http://127.0.0.1:'+PHONE_RELAY_PORT,'--no-autoupdate'],{windowsHide:true,stdio:['ignore','pipe','pipe']});
  let settled=false;const onData=chunk=>{const line=chunk.toString();const match=line.match(/https:\/\/[a-z0-9-]+\.trycloudflare\.com/i);if(match){phonePublicUrl=match[0];console.log('Secure phone camera link ready: '+phonePublicUrl);}};
  child.stdout.on('data',onData);child.stderr.on('data',onData);child.on('error',()=>{if(!settled)tryStart();});child.on('spawn',()=>{settled=true;tunnelProcess=child;});child.on('exit',()=>{if(tunnelProcess===child){tunnelProcess=null;phonePublicUrl=null;}});
 };
 tryStart();
}
let stopping=false;
function stopAll(){if(stopping)return;stopping=true;clearInterval(sessionsCleanup);try{backend.kill();}catch{}try{cameraAgent.kill();}catch{}try{tunnelProcess?.kill();}catch{}try{relayServer.close();server.close();backendOut.end();backendErr.end();}catch{}}
process.on('exit',stopAll);process.on('SIGINT',()=>{stopAll();process.exit(0);});process.on('SIGTERM',()=>{stopAll();process.exit(0);});
backend.on('error',e=>fs.appendFileSync(path.join(logDir,'backend.log'),String(e)+'\n'));
function waitForBackend(attempt=0){const probe=http.get({hostname:'127.0.0.1',port:BACKEND_PORT,path:'/health',timeout:1200},r=>{r.resume();if(r.statusCode!==200)return retry();relayServer.listen(PHONE_RELAY_PORT,'127.0.0.1',()=>{server.listen(PORT,'127.0.0.1',()=>{const url='http://127.0.0.1:'+PORT;execFile('cmd',['/c','start','',url],{windowsHide:true});console.log('MediaAIStudio is running at '+url);startTunnel();});});});probe.on('error',retry);probe.on('timeout',()=>{probe.destroy();retry();});function retry(){if(attempt>=30){console.error('Backend did not start. See logs/backend.log.');stopAll();process.exit(1);return;}setTimeout(()=>waitForBackend(attempt+1),1000);}}
waitForBackend();
