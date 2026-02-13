// autoloading

// preloading (for faster webpack builds)
import moment from 'moment-timezone';
import chrome from 'ui/chrome';
import routes from 'ui/routes';
import modules from 'ui/modules';

//import kibanaLogoUrl from 'ui/images/kibana.svg';
import kibanaLogoUrl from 'ui/images/infrared.png';
import 'ui/autoload/all';
import 'plugins/infrared/explore/index';
import 'plugins/infrared/summary/index';

// asit - index-pattern
// setting causes a load failure with
// Error: [illegal_argument_exception] The parameter [fields] is no longer supported,
// please use [stored_fields] to retrieve stored fields or _source filtering
// if the field is not stored
// todo: remove from project
// dashboard, settings and doc
// import 'plugins/infrared/dashboard/index';
// import 'plugins/infrared/settings/index';
// import 'plugins/infrared/doc';

import 'ui/vislib';
import 'ui/agg_response';
import 'ui/agg_types';
import 'ui/timepicker';
import 'leaflet';

import 'plugins/infrared/graph/index';

routes.enable();

routes
.otherwise({
  redirectTo: `/${chrome.getInjected('kbnDefaultAppId', 'graph')}`
});

chrome
.setTabDefaults({
  resetWhenActive: true,
  lastUrlStore: window.sessionStorage,
  activeIndicatorColor: '#656a76'
})
.setRootController('kibana', function ($scope, $rootScope, courier, config) {
  function setDefaultTimezone() {
    moment.tz.setDefault(config.get('dateFormat:tz'));
  }

  // wait for the application to finish loading
  $scope.$on('application.load', function () {
    courier.start();
  });

  $scope.$on('init:config', setDefaultTimezone);
  $scope.$on('change:config.dateFormat:tz', setDefaultTimezone);
});
