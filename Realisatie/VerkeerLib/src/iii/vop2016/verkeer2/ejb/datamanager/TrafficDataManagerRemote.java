/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iii.vop2016.verkeer2.ejb.datamanager;

import javax.ejb.Remote;

/**
 *
 * @author tobia
 */
@Remote
public interface TrafficDataManagerRemote {
    
    public void downloadNewData();
    
}