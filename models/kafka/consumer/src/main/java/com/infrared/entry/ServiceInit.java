package com.infrared.entry;

import com.infrared.util.CassandraDB;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Created by asitk on 4/8/16.
 */
public class ServiceInit {
    public static void Init(String[] args) throws Exception {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        Server jettyServer = new Server(9999);

        jettyServer.setHandler(context);

        ServletHolder jerseyServlet = context.addServlet(
                org.glassfish.jersey.servlet.ServletContainer.class, "/*");
        jerseyServlet.setInitOrder(0);

        // Tells the Jersey Servlet which REST service/class to load.
        jerseyServlet.setInitParameter(
                "jersey.config.server.provider.classnames",
                service.class.getCanonicalName());

        try {
            CassandraDB.start();
            jettyServer.start();
            jettyServer.join();
        } finally {
            CassandraDB.shutdown();
            jettyServer.destroy();
        }
    }

    public static ResourceConfig createApp() {
        return new ResourceConfig();
        // .packages("org.glassfish.jersey.examples.jsonmoxy")
        // .register(createMoxyJsonResolver());
    }
}
