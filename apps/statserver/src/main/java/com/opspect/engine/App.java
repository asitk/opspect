package com.opscog.engine;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
// import org.glassfish.jersey.moxy.json.MoxyJsonConfig;
import org.glassfish.jersey.server.ResourceConfig;

public class App {
  public static void main(String[] args) throws Exception {
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");

    Server jettyServer = new Server(9999);
    jettyServer.setHandler(context);

    ServletHolder jerseyServlet =
        context.addServlet(org.glassfish.jersey.servlet.ServletContainer.class, "/*");
    jerseyServlet.setInitOrder(0);

    // Tells the Jersey Servlet which REST service/class to load.
    jerseyServlet.setInitParameter(
        "jersey.config.server.provider.classnames", service.class.getCanonicalName());

    try {
      jettyServer.start();
      jettyServer.join();
    } finally {
      jettyServer.destroy();
    }
  }

  public static ResourceConfig createApp() {
    return new ResourceConfig();
    // .packages("org.glassfish.jersey.examples.jsonmoxy")
    // .register(createMoxyJsonResolver());
  }

  /*public static ContextResolver<MoxyJsonConfig> createMoxyJsonResolver() {
      final MoxyJsonConfig moxyJsonConfig = new MoxyJsonConfig();
      Map<String, String> namespacePrefixMapper = new HashMap<String, String>(1);
      namespacePrefixMapper.put("http://www.w3.org/2001/XMLSchema-instance", "xsi");
      moxyJsonConfig.setNamespacePrefixMapper(namespacePrefixMapper).setNamespaceSeparator(':');
      return moxyJsonConfig.resolver();
  }*/
}
