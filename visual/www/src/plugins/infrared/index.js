import ingest from './server/routes/api/ingest';
import search from './server/routes/api/search';

module.exports = function (kibana) {
  return new kibana.Plugin({
    id: 'infrared',
    config: function (Joi) {
      return Joi.object({
        enabled: Joi.boolean().default(true),
        defaultAppId: Joi.string().default('graph'),
        index: Joi.string().default('.kibana')
      }).default();
    },

    uiExports: {
      app: {
        id: 'infrared',
        title: 'Infrared',
        listed: false,
        description: '',
        //icon: 'plugins/kibana/settings/sections/about/barcode.svg',
        main: 'plugins/infrared/infrared',        // public/infrared.js
        uses: [
          'visTypes',
          'spyModes',
          'fieldFormats',
          'navbarExtensions',
          'settingsSections',
          'docViews'
        //]
        ],

        injectVars: function (server, options) {
          let config = server.config();

          return {
            kbnDefaultAppId: config.get('kibana.defaultAppId')
            //kbnDefaultAppId: config.get('graph')
          };
        },
      },

      links: [
        {
          title: 'Service',
          order: 2001,
          url: '/app/infrared#/graph',
          description: 'Service Overview',
          icon: 'plugins/infrared/assets/dashboard.svg',
        },
        {
          title: 'Explore',
          order: 2002,
          url: '/app/infrared#/explore',
          description: 'Explore',
          icon: 'plugins/infrared/assets/discover.svg',
        },
        /*{
          title: 'Summary',
          order: 2003,
          url: '/app/infrared#/summary',
          description: 'Dashboard',
          icon: 'plugins/infrared/assets/visualize.svg',
        },
        {
          title: 'Admin',
          order: 2004,
          url: '/app/infrared#/explore',
          description: 'design data visualizations',
          icon: 'plugins/infrared/assets/settings.svg',
        },*/

        /* asit - index pattern
        {
          title: 'Error Log',
          order: 2003,
          url: '/app/infrared#/dashboard',
          description: 'compose visualizations for much win',
          icon: 'plugins/infrared/assets/dashboard.svg',
        }*/
      ],
      injectDefaultVars(server, options) {
        return {
          kbnIndex: options.index
        };
      },
    },

    init: function (server, options) {
      // asit - index pattern
      ingest(server);
      search(server);
    }
  });

};
