
/* global Materialize, L */

var timerProgress = 0;
var map;
var mymap;
var layer;
var trafficData = [];
var modus = "live";

$.ajax({
    url: urlTimerNewData,
    dataType: "json",
    success: initTimer,
    error: function(jqXHR, textStatus, errorThrown ){
        Materialize.toast('Er kan geen data worden opgehaald over de timer op de server!', 4000, 'toast bottom error');
    }
});

function setTimerProgress(){
    timerProgress += 0.5;
    if(timerProgress%100 === 0){
        timerProgress = 0;
        refreshLiveData();
    }
    progressBar = $("#timerProgress");
    progressBar.attr("style","width:"+timerProgress+"%;");
}

function initTimer(data){
    var interval = Math.floor(data.interval/200*1000);
    setInterval(setTimerProgress,interval);
    timerProgress = data.percentDone;
}

function refreshLiveData(){
    $.ajax({
        url: urlAllRoutes,
        dataType: "json",
        success: function(data, textStatus, jqXHR ){
            Materialize.toast('De verkeerssituatie werd zonet geüpdatet', 4000, 'toast bottom success');
            trafficData = data;
            setModus(modus);
        },
        error: function(jqXHR, textStatus, errorThrown ){
            Materialize.toast('Er kan geen nieuwe data worden opgehaald!', 4000, 'toast bottom error');
            trafficList = $(".traffic-list");
            trafficListItem = $("<li/>").text("Geen trajecten om weer te geven...");
            trafficList.add(trafficListItem);
        }
    });
}

function initMap(){
    /*map = new google.maps.Map(document.getElementById('map'), {
          center: {lat: 51.038901, lng: 3.725215},
          scrollwheel: false,
          navigationControl: false,
          mapTypeControl: false,
          scaleControl: false,
          mapTypeId: google.maps.MapTypeId.ROADMAP,
          zoom: 15
    });
    initGUI();
    */
}

function initGUI(){
    liveTab = $("<li/>").append($("<a/>").attr("href","#!").text("Live").click(setLiveModus).attr("id","btnSwitchToLive")).addClass("active");
    avgTab = $("<li/>").append($("<a/>").attr("href","#!").text("Gemiddeld").click(setAvgModus).attr("id","btnSwitchToAvg"));
    
    nav = $("<ul/>").addClass("pagination");
    nav.append(liveTab).append(avgTab);
    mapSwitch = $("<div/>").addClass("mapSwitch").append(nav);
    $("#map").append(mapSwitch);
}


function setLiveMap(){
    L.tileLayer('https://api.tiles.mapbox.com/v4/{id}/{z}/{x}/{y}.png?access_token={accessToken}', {
        attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>, Imagery © <a href="http://mapbox.com">Mapbox</a>',
        maxZoom: 18,
        id: 'tobiasvdp.ac4aa6b2',
        accessToken: 'pk.eyJ1IjoidG9iaWFzdmRwIiwiYSI6ImNpbGpxcTFwaTAwYjF3NGx6bWZ2bGZkcG8ifQ.DTe2IBQLNc9zQa62kD-4_g'
    }).addTo(mymap);
    requestGeoJson();  
}

function setAvgMap(){
    //teken Avg data
}

function switchBtnModus(){
    if(modus === "live"){
        $("#btnSwitchToLive").parent().addClass("active");
        $("#btnSwitchToAvg").parent().removeClass("active");
    }
        
    if(modus === "avg"){
        $("#btnSwitchToAvg").parent().addClass("active");
        $("#btnSwitchToLive").parent().removeClass("active");
    } 
}


function setModus(modus){
    if(modus === "live")
        setLiveModus();
    if(modus === "avg")
        setAvgModus();
}

function setLiveModus(){
    modus = "live";
    setLiveMap();
    setLiveList();
    switchBtnModus();
}

function setAvgModus(){
    modus = "avg";
    setAvgMap();
    setAvgList();
    switchBtnModus();
}

function setLiveList(){
    trafficListBox = $("#traffic-list");
    trafficListBox.html("");
    
    header = $("<ul/>").addClass("traffic-list")
            .append($("<li/>").append($("<table/>").addClass("highlight").append($("<thead/>")
            .append($("<tr/>")
            .append($("<th/>").text("Traject").attr("width","50%").attr("data-field","id"))
            .append($("<th/>").text("Reistijd").attr("width","20%").attr("data-field","duration").addClass("center"))
            .append($("<th/>").text("Vertraging").attr("width","20%").attr("data-field","delay").addClass("center"))
            .append($("<th/>").attr("width","10%"))
    ))));
    trafficListBox.append(header);
    
    trafficList = $(".traffic-list");
    if(trafficData.length>0){
        for (var i = 0; i < trafficData.length; i++) {
            var duration = {"min":0,"sec":0};
            var delay = {"min":0,"sec":0};
            var id, name, delay, trend, delayClass, delayTxt, durationTxt;
            id = trafficData[i].id;
            name = trafficData[i].name;
            duration.min = Math.round((trafficData[i].currentDuration)/60);
            duration.sec = (trafficData[i].currentDuration-duration.min);
            durationTxt = duration.min+" min";
            if(trafficData[i].optimalDuration >= 0){
                delay.sec = trafficData[i].currentDuration - trafficData[i].optimalDuration;
                delay.min = Math.round(delay.sec/60);
                delayTxt = delay.min+" min";
            }else{
                delayTxt = "? min";
            }
            if(trafficData[i].trend < 0){
                trend = "call_received";
            }else if(trafficData[i].trend > 0){
                trend = "call_made";
            }else{
                trend = "";
            }
            trend = "call_made";
            
            delayClass = "default";
            switch(trafficData[i].currentDelayLevel){
                case 0: delayClass = "veryfast"; break;
                case 1: delayClass = "fast"; break;
                case 2: delayClass = "intermediate"; break;
                case 3: delayClass = "slow"; break;
                case 4: delayClass = "verslow"; break;
            }

            trafficListItem = $("<li/>").attr("id","route"+id).append($("<table/>").addClass("highlight").append($("<thead/>")
                    .append($("<tr/>")
                    .append($("<td/>").text(name).attr("width","50%"))
                    .append($("<td/>").text(durationTxt).attr("width","20%").addClass("center"))
                    .append($("<td/>").append($("<span/>").addClass("badge "+delayClass).text(delayTxt)).attr("width","20%").addClass("center"))
                    .append($("<td/>").attr("width","10%").append($("<i/>").addClass("material-icons").text(trend)))
            )));
            trafficList.append(trafficListItem);
        }
    }else{
        trafficListItem = $("<li/>").text("Geen trajecten om weer te geven...");
        trafficList.append(trafficListItem);
    }
    Materialize.showStaggeredList('.traffic-list');
}

function setAvgList(){
    trafficListBox = $("#traffic-list");
    trafficListBox.html("");
    
    header = $("<ul/>").addClass("traffic-list")
            .append($("<li/>").append($("<table/>").addClass("highlight").append($("<thead/>")
            .append($("<tr/>")
            .append($("<th/>").text("Traject").attr("width","50%").attr("data-field","id"))
            .append($("<th/>").text("Reistijd").attr("width","20%").attr("data-field","duration").addClass("center"))
            .append($("<th/>").text("Vertraging").attr("width","20%").attr("data-field","delay").addClass("center"))
            .append($("<th/>").attr("width","10%"))
    ))));

    trafficListBox.append(header);

    trafficList = $(".traffic-list");
    if(trafficData.length>0){
        for (var i = 0; i < trafficData.length; i++) {
            id = trafficData[i].id;
            name = trafficData[i].name;
            duration = "8 min";
            delay = "1 min";

            trafficListItem = $("<li/>").append($("<table/>").addClass("highlight").append($("<thead/>")
                    .append($("<tr/>")
                    .append($("<td/>").text(name).attr("width","50%"))
                    .append($("<td/>").text(duration).attr("width","20%").addClass("center"))
                    .append($("<td/>").append($("<span/>").addClass("badge slow").text(delay)).attr("width","20%").addClass("center"))
                    .append($("<td/>").attr("width","10%"))
            )));
            trafficList.append(trafficListItem);
        }
    }else{
        trafficListItem = $("<li/>").text("Geen trajecten om weer te geven...");
        trafficList.append(trafficListItem);
    }
    Materialize.showStaggeredList('.traffic-list');
}


    
function highlightRouteInList(routeid){
    $("#traffic-list ul li").removeClass("active");
    $("#route"+routeid).addClass("active");
    $('.sidebar').animate({
        scrollTop: $('#traffic-list #route'+routeid).offset().top
    }, 'slow');
}
    
function setGeoJson(data){
    
    if(layer != undefined){
        mymap.removeLayer(layer);
    }
    
    console.log(data);
    
    layer = L.geoJson(data, {
        
        style: function (feature) {
            var colorClass = "default";
            switch(feature.properties.currentDelayLevel){
                case 0: colorClass = "veryfast"; break;
                case 1: colorClass = "fast"; break;
                case 2: colorClass = "intermediate"; break;
                case 3: colorClass = "slow"; break;
                case 4: colorClass = "veryslow"; break;
            }
            var color = $("."+colorClass).first().css("background-color")
            return {color: color};
        },
        onEachFeature: function (feature, layer) {
            
            layer.on('click', function(e){
                console.log(feature.properties);
                $("#text").text("route " + feature.properties.description);
                
                //functie voor aanroepen hilight in tabel
                //click event that triggers the popup and centres it on the polygon
                
                var popup = L.popup()
                .setLatLng(e.latlng)
                .setContent("<h5>Route</h5> <p> ID = "+feature.properties.description+"</p>")
                .openOn(mymap);
        
                highlightRouteInList(feature.properties.description);
                
                click = true;
        
        
            });
            layer.on('mouseover', function(){
                //$("#text").text("route " + feature.properties.description);
                //functie voor aanroepen hilight in tabel
                click = false;
                highlightRouteInList(feature.properties.description);
            });
            layer.on('mouseout', function(){
                if(!click){
                    $("#traffic-list ul li").removeClass("active");
                }
                click = false;
            });
        }
    }).addTo(mymap);    
}



function failedCall(data){
    Materialize.toast("Er is onmogelijk data op te halen", 4000, 'toast bottom error') // 4000 is the duration of the toast
}


function requestGeoJson(){
    $.ajax({
        url: urlGeoJSON,
        dataType: "json",
        success: setGeoJson,
        error:failedCall
    });
}


$(document).ready(function() {
    mymap = L.map('map').setView([51.096434, 3.744511], 11);
    initGUI();
    setModus("live");
    refreshLiveData();
    //requestGeoJson();
});




