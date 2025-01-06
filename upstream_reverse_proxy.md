[[_TOC_]]
# Configuration in Reverse Proxy Server
## Nginx 
```bash
user nginx; 
worker_processes auto;
error_log /var/log/nginx/error.log warn;
pid /var/run/nginx.pid;
events {
   worker_connections 1024;
}  
http {
   server_tokens off;

   include /etc/nginx/mime.types; 
   default_type application/octet-stream; 
   (ommitted... )
   server { 
   listen  80;                                
       client_max_body_size 100m;  
       (ommitted... )

       location /api/v1 {
            proxy_pass_request_headers on;

            # proxy_set_header Host $host;
            # proxy_set_header X-Forwarded-Prefix '/api/v1';
            # proxy_pass http://map/api/v1;
            
            rewrite /api/v1/(.*) /$1  break;  # path: '/api/v1' -> '/'
            proxy_set_header Xauthorization YWRtaW46cGFzc3dvcmQ=; # aws api gateway's header
            proxy_set_header Host 5szwlvd4ng.execute-api.ap-northeast-1.amazonaws.com; # aws gateway url
            proxy_pass https://5szwlvd4ng.execute-api.ap-northeast-1.amazonaws.com; # aws gateway url
        }

        
```
## Traefik 
* traefik.yml
```yml
entryPoints:
  web:
   address: ":80"
  tcp-7001:
   address: ":7001"  
api:
  insecure: true
  dashboard: true

# Dynamic configuration
providers:
  file:
    # filename: "traefik-dynamic-conf.yml"
    directory : /etc/traefik/config
    watch : true
    filename : traefik-dynamic-conf
    debugLogGeneratedTemplate : true
```
* traefik-dynamic-conf.yml
```yml
# Dynamic configuration

http:
 routers:
   router0:
     entryPoints:
     - web      
     service: pets
     rule: Path(`/pets`)

 services:
   map:
     loadBalancer:
       servers:
       - url: "https://5szwlvd4ng.execute-api.ap-northeast-1.amazonaws.com"  # aws gateway url
tcp:
 routers:
   router0:
     entryPoints:
     - "tcp-7001"
     rule : HostSNI(`*`)
     service: mologicservice-l7001
 services:  
   mologicservice-l7001:
     loadBalancer:
       servers:
       - address: "mologicservice-l7001:7001"
```
* docker cli
  ```bash
   docker run -d -p 8080:8080 \
                 -p 80:80 \
                 -p 7001:7001  \
                 -v $PWD/traefik.yml:/etc/traefik/traefik.yml \
                 -v $PWD/traefik-dynamic-conf.yml:/etc/traefik/config/traefik-dynamic-conf.yml \
                 -v /var/run/docker.sock:/var/run/docker.sock \
                 traefik:v2.5
  ```
