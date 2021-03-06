/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iii.vop2016.verkeer2.war.rest;

import iii.vop2016.verkeer2.ejb.components.IGeoLocation;
import iii.vop2016.verkeer2.ejb.components.IRoute;
import iii.vop2016.verkeer2.ejb.dao.IGeneralDAO;
import iii.vop2016.verkeer2.ejb.dataprovider.IDataProvider;
import iii.vop2016.verkeer2.ejb.geojson.GeoJsonRemote;
import iii.vop2016.verkeer2.ejb.geojson.IGeoJson;
import iii.vop2016.verkeer2.ejb.helper.BeanFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.enterprise.context.RequestScoped;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.xml.ws.WebServiceContext;

/**
 * REST Web Service
 *
 * @author tobia
 */
@Path("geojson")
@RequestScoped
public class GeojsonResource {

    @Context
    private UriInfo context;

    public GeojsonResource() {
    }

    @Resource
    private WebServiceContext webServiceContext;
    private InitialContext ctx;
    private static BeanFactory beans;

    @PostConstruct
    private void init() {
        try {
            ctx = new InitialContext();
        } catch (NamingException ex) {
            Logger.getLogger(RoutesResource.class.getName()).log(Level.SEVERE, null, ex);
        }
        beans = BeanFactory.getInstance(ctx, null);
    }

    @GET
    @Path("{routeId}/current")
    @Produces("application/json")
    public Response getGeoJson(@PathParam("routeId") String routeString) {
        try {
            long routeId = Long.parseLong(routeString);
            IRoute route = beans.getGeneralDAO().getRoute(routeId);
            if (route == null) {
                return Response.status(Response.Status.OK).entity("").build();
            }

            IGeoJson provider = beans.getGeoJsonProvider();
            IDataProvider dataProvider = beans.getDataProvider();
            if (dataProvider != null && provider != null) {
                List<IGeoLocation> list = provider.getRoutePlotGeoLocations(route);

                Map<IRoute, List<IGeoLocation>> map = new HashMap<>();
                map.put(route, list);

                Map<IRoute, Integer> delaylevels = new HashMap<>();
                delaylevels.put(route, dataProvider.getCurrentDelayLevel(route, null));

                return Response.status(Response.Status.OK).entity(provider.getGeoJson(map, delaylevels)).build();
            }
            return Response.status(Response.Status.OK).entity("").build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }

    @GET
    @Path("all/current")
    @Produces("application/json")
    public Response getGeoJson() {
        try {
            IGeneralDAO dao = beans.getGeneralDAO();
            IDataProvider dataProvider = beans.getDataProvider();

            if (dao == null || dataProvider == null) {
                return Response.status(Response.Status.OK).entity("").build();
            }

            List<IRoute> routes = beans.getGeneralDAO().getRoutes();

            if (routes == null) {
                return Response.status(Response.Status.OK).entity("").build();
            }

            Map<IRoute, List<IGeoLocation>> map = new HashMap<>();
            Map<IRoute, Integer> delaylevels = new HashMap<>();

            IGeoJson provider = beans.getGeoJsonProvider();
            for (IRoute route : routes) {
                List<IGeoLocation> list = provider.getRoutePlotGeoLocations(route);
                map.put(route, list);
                delaylevels.put(route, dataProvider.getCurrentDelayLevel(route, null));
            }

            return Response.status(Response.Status.OK).entity(provider.getGeoJson(map, delaylevels)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }

    @GET
    @Path("{routeId}/avg")
    @Produces("application/json")
    public Response getAvgGeoJson(@PathParam("routeId") String routeString) {
        try {
            long routeId = Long.parseLong(routeString);
            IRoute route = beans.getGeneralDAO().getRoute(routeId);
            if (route == null) {
                return Response.status(Response.Status.OK).entity("").build();
            }

            IGeoJson provider = beans.getGeoJsonProvider();
            IDataProvider dataProvider = beans.getDataProvider();
            if (dataProvider != null && provider != null) {
                List<IGeoLocation> list = provider.getRoutePlotGeoLocations(route);

                Map<IRoute, List<IGeoLocation>> map = new HashMap<>();
                map.put(route, list);

                Map<IRoute, Integer> delaylevels = new HashMap<>();
                delaylevels.put(route, dataProvider.getAvgDelayLevel(route, null));

                return Response.status(Response.Status.OK).entity(provider.getGeoJson(map, delaylevels)).build();
            }
            return Response.status(Response.Status.OK).entity("").build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }

    @GET
    @Path("all/avg")
    @Produces("application/json")
    public Response getAvgGeoJson() {
        try {
            IGeneralDAO dao = beans.getGeneralDAO();
            IDataProvider dataProvider = beans.getDataProvider();

            if (dao == null || dataProvider == null) {
                return Response.status(Response.Status.OK).entity("").build();
            }

            List<IRoute> routes = beans.getGeneralDAO().getRoutes();

            if (routes == null) {
                return Response.status(Response.Status.OK).entity("").build();
            }

            Map<IRoute, List<IGeoLocation>> map = new HashMap<>();
            Map<IRoute, Integer> delaylevels = new HashMap<>();

            IGeoJson provider = beans.getGeoJsonProvider();
            for (IRoute route : routes) {
                List<IGeoLocation> list = provider.getRoutePlotGeoLocations(route);
                map.put(route, list);
                delaylevels.put(route, dataProvider.getAvgDelayLevel(route, null));
            }

            return Response.status(Response.Status.OK).entity(provider.getGeoJson(map, delaylevels)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
        }
    }
}
