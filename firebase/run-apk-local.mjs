// Local test transport gate. No credentials/data logging and no non-loopback targets.
import net from 'node:net';import http from 'node:http';import {spawn} from 'node:child_process';
let online=true;const sockets=new Set();
const proxy=net.createServer(client=>{if(!online){client.destroy();return;}const upstream=net.connect(18080,'127.0.0.1');
 for(const s of [client,upstream]){sockets.add(s);s.on('error',()=>{client.destroy();upstream.destroy();});s.on('close',()=>{sockets.delete(s);client.destroy();upstream.destroy();});}
 client.pipe(upstream);upstream.pipe(client);
});
const control=http.createServer((req,res)=>{if(req.url==='/offline'){online=false;for(const s of sockets)s.destroy();}else if(req.url==='/online')online=true;else if(req.url!=='/health'){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type','application/json');res.end(JSON.stringify({online}));});
await Promise.all([new Promise((ok,no)=>proxy.once('error',no).listen(18081,'127.0.0.1',ok)),new Promise((ok,no)=>control.once('error',no).listen(18082,'127.0.0.1',ok))]);
const configuration=process.argv[2]??'Portrait', serial=process.argv[3]??'emulator-5554';
if(!['Portrait','LargeLandscape'].includes(configuration)||!/^emulator-\d+$/.test(serial))throw new Error('Explicit local test arguments required');
const child=spawn('pwsh',['-NoProfile','-File','scripts/verify-h07-apk.ps1','-Serial',serial,'-Configuration',configuration,'-LocalSDK'],{stdio:'inherit'});
const code=await new Promise(resolve=>{child.on('error',()=>resolve(1));child.on('exit',c=>resolve(c??1));});
for(const s of sockets)s.destroy();await Promise.all([new Promise(r=>proxy.close(r)),new Promise(r=>control.close(r))]);process.exitCode=code;
