/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iii.vop2016.verkeer2.ejb.dao;

import iii.vop2016.verkeer2.ejb.components.IRoute;
import iii.vop2016.verkeer2.ejb.components.IRouteData;
import iii.vop2016.verkeer2.ejb.components.Route;
import iii.vop2016.verkeer2.ejb.components.RouteData;
import java.io.Serializable;
import java.util.Date;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

/**
 *
 * @author Mike
 */
@Entity
@Table(name="routedata")
@Access(AccessType.PROPERTY)
public class RouteDataEntity extends RouteData{


    public RouteDataEntity() {
        super();
    }

    public RouteDataEntity(IRouteData component) {
        super();
        this.distance = component.getDistance();
        this.duration = component.getDuration();
        this.id = component.getId();
        this.route = component.getRoute();
        this.timestamp = component.getTimestamp();
        this.provider = component.getProviderName();
    }
  
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public long getId() {
        return super.getId();
    }

    @Override
    @Transient
    public IRoute getRoute() {
        return super.getRoute();
    }
    
    public long getRouteID(){
        if(route == null)
            return 0;
        return super.getRoute().getId();
    }
    
    public void setRouteID(long id){
        if(route == null)
            route = new Route();
        route.setId(id);
    }

    @Override
    public int getDuration() {
        return super.getDuration();
    }

    @Override
    public int getDistance() {
        return super.getDistance();
    }

    @Override
    @Temporal(TemporalType.TIMESTAMP)
    public Date getTimestamp() {
        return super.getTimestamp();
    }

    @Override
    public void setDistance(int distance) {
        super.setDistance(distance); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setDuration(int duration) {
        super.setDuration(duration); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setId(long id) {
        super.setId(id); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setTimestamp(Date timestamp) {
        super.setTimestamp(timestamp); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getProviderName() {
        return super.getProviderName();
    }

    @Override
    public void setProviderName(String providerName) {
        super.setProviderName(providerName);
    }
    
}