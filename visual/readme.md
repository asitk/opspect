package.json
 --> "start": "sh ./bin/kibana --dev", 
 run --> cli or cli_plugin

src/cli - entry point, manage cluster, read yml
src/cli - base_path_proxy.js .. proxy
src/cli/server.js - Initial server setting including logging level
src/cli/cluster/base_path_proxy.js - setup route for basepath + kibana path

// wrapper directive, which sets some global stuff up like the left nav
optimize/bundles/kibana_bundle.js

server/kbnserver --- main class
server/http - server object (hapi) -- expose static dirs
server/plugins/plugins.js -- resolve routes to plugins, expose static dirs
server/plugins/plugin_api.js -- wrapper for plugins.js

ui/public/chrome/api/template.js
chrome.setRootController = function (as, controllerName) 

node --debug /mnt/data/src/bitbucket.org/infrared/visual/www/src/cli/index.js --dev --no-ssl --no-base-path --no-watch --verbose

--server.port=5610
--server.xsrf.disableProtection=true
--optimize.enabled=false

--optimize.lazyPort=5611,
--optimize.lazyPrebuild=true,

http://localhost:5601/app/kibana#/dashboard/dashboard?_g=(refreshInterval:(display:Off,pause:!f,value:0),time:(from:'2016-05-02T16:30:00.000Z',mode:absolute,to:'2016-05-02T18:00:00.000Z'))&_a=(filters:!(),options:(darkTheme:!t),panels:!((col:1,id:Count,panelIndex:1,row:1,size_x:3,size_y:2,type:visualization),(col:6,id:GET,panelIndex:2,row:1,size_x:3,size_y:2,type:visualization),(col:10,id:POST,panelIndex:3,row:1,size_x:3,size_y:2,type:visualization),(col:1,id:GET-Vs-POST,panelIndex:4,row:3,size_x:5,size_y:2,type:visualization),(col:6,id:May-2nd,panelIndex:5,row:3,size_x:3,size_y:2,type:visualization),(col:10,id:User-Agent-Breakdown,panelIndex:6,row:3,size_x:3,size_y:2,type:visualization),(col:1,columns:!(_source),id:'May-2nd-10:00-11:30',panelIndex:7,row:5,size_x:12,size_y:5,sort:!('@timestamp',desc),type:search)),query:(query_string:(analyze_wildcard:!t,query:'*')),title:dashboard,uiState:())

code structure

kibana 
... public --> Angular code
...    assets --> images
...    dashboard (Menu 1)
...           Components --> Modules
...           Directives 
...           Partials   --> Angular HTML Templates
...           Services
...           Styles

...     discover (Menu 2)

...     index.html
...     index.js  --> Top level entry point

www setup

npm install font-awesome --save
npm install vis --save
npm install highcharts --save
npm install jqwidgets-framework --save
npm install img-loader --save
npm install moment --save
npm install moment-timezone --save
npm install jstz --save

Variable Baselines
Hourly (Basic Unit)
Time Range in Hrs (8am - 12pm), (12pm - 2pm)
Daily (Hourly + Day of Week) 
Weekly
Monthly

App taking more load over the weekend

say you have a baseline that uses the weekly trend and with the time period configured for 30 days. At a given day and time, say Monday 10:30 AM, the baseline in effect is one that considers only the data accumulated on the same hour and day of the week over the last 30 days

A monthly trend calculates the baseline from data accumulated at the same hour but only on the same day of the month. So, for example, on January 5th at 10:30 AM, the baseline is established based on data accumulated at the same hour on the 5th of each month for the prior year (365 days)
