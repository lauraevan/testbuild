const VERSION="minecraft-26.3-stream-v1";

self.addEventListener("install",event=>{
  self.skipWaiting();
});

self.addEventListener("activate",event=>{
  event.waitUntil(self.clients.claim());
});

self.addEventListener("message",event=>{
  if(event.data?.type==="SKIP_WAITING") self.skipWaiting();
});

self.addEventListener("fetch",event=>{
  const url=new URL(event.request.url);
  if(url.pathname.endsWith("/minecraft-26.3/game.html")){
    event.respondWith(streamGame());
  }
});

async function streamGame(){
  const manifestUrl=new URL("./manifest.json",self.location.href);
  const manifestResponse=await fetch(manifestUrl,{cache:"no-store"});
  if(!manifestResponse.ok) return new Response("Minecraft 26.3 build is still publishing.",{status:503});
  const manifest=await manifestResponse.json();
  const chunks=manifest.chunks||[];
  if(!chunks.length) return new Response("Minecraft 26.3 chunk manifest is empty.",{status:503});

  let index=0;
  let reader=null;

  const body=new ReadableStream({
    async pull(controller){
      try{
        while(true){
          if(!reader){
            if(index>=chunks.length){
              controller.close();
              return;
            }
            const chunkUrl=new URL(chunks[index++],manifestUrl);
            const response=await fetch(chunkUrl,{cache:"force-cache"});
            if(!response.ok) throw new Error("Chunk fetch failed: "+response.status+" "+chunkUrl.pathname);
            if(!response.body) throw new Error("Streaming body unavailable for "+chunkUrl.pathname);
            reader=response.body.getReader();
          }

          const result=await reader.read();
          if(result.done){
            reader=null;
            continue;
          }
          controller.enqueue(result.value);
          return;
        }
      }catch(error){
        controller.error(error);
      }
    },
    cancel(reason){
      if(reader) reader.cancel(reason).catch(()=>{});
    }
  });

  return new Response(body,{
    status:200,
    headers:{
      "Content-Type":"text/html; charset=utf-8",
      "Cache-Control":"no-store",
      "X-Minecraft-Version":"26.3",
      "X-Stream-Build":VERSION
    }
  });
}
