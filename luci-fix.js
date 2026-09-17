(function(){
  const OldFetch=window.fetch.bind(window);
  const MODEL='SmolLM2-360M-Instruct-q4f32_1-MLC';
  let enginePromise;
  async function engine(){
    if(enginePromise)return enginePromise;
    if(!window.isSecureContext||!navigator.gpu) throw new Error('WebGPU unavailable');
    enginePromise=(async()=>{
      const mod=await import('https://esm.run/@mlc-ai/web-llm');
      return mod.CreateMLCEngine(MODEL,{initProgressCallback:p=>{try{const s=document.getElementById('status');if(s&&p&&p.text)s.textContent=p.text}catch(e){}}});
    })().catch(e=>{enginePromise=null;throw e});
    return enginePromise;
  }
  window.fetch=async function(input,init){
    const url=typeof input==='string'?input:(input&&input.url)||'';
    if(!(/text\.pollinations\.ai|gen\.pollinations\.ai|\/api\/chat/i.test(url))) return OldFetch(input,init);
    try{
      let body={};
      try{body=JSON.parse((init&&init.body)||'{}')}catch(e){}
      const msgs=Array.isArray(body.messages)?body.messages:[];
      const system=(msgs.find(m=>m.role==='system')||{}).content||'You are LuciChat, a helpful, concise and safe AI assistant. Answer factual questions clearly.';
      const history=msgs.filter(m=>m.role==='user'||m.role==='assistant').slice(-8).map(m=>({role:m.role,content:String(m.content||'')}));
      const e=await engine();
      const r=await e.chat.completions.create({messages:[{role:'system',content:system},...history],temperature:.5,max_tokens:220});
      const answer=String(r?.choices?.[0]?.message?.content||'').trim();
      if(!answer) throw new Error('Empty local response');
      return new Response(JSON.stringify({choices:[{message:{role:'assistant',content:answer},text:answer}],text:answer}),{status:200,headers:{'Content-Type':'application/json'}});
    }catch(err){
      return new Response(JSON.stringify({error:{message:err.message||'Local AI unavailable'}}),{status:503,headers:{'Content-Type':'application/json'}});
    }
  };
})();
