
var lastSelectedRoute = null;
var addedPeriods = 0;


showRoutePreview = function(){
    routeID = $("#"+$(this).attr("for")).val();
    showRoutePreview(routeID);
    
};
hideRoutePreview = function(){
    if(lastSelectedRoute === null)
        hideRoutePreview();
    else
        showRoutePreview(lastSelectedRoute);
    
};
addRouteToList = function(){
    lastSelectedRoute = $("#"+$(this).attr("for")).val();
};
    
    
    
function setRouteMultiplicity(multiplicity){
    if(multiplicity === undefined) {
        multiplicity = "single";
    }
    switch(multiplicity){
        case "multi": 
            $("[name=routeId]").attr("type","checkbox");
            $("[name=routetype]").attr("value","multi");    
            $("#btnSelectAllRoutes").show();
            break;
        case "single": 
            $("#btnSelectAllRoutes").hide();
            $("[name=routeId]").attr("type","radio");
            $("[name=routetype]").attr("value","single");    
            break;
    }
}

function selectAllRoutes(){
    $('[name=routeId]').each(function() { //loop through each checkbox
        this.checked = true;  //select all checkboxes with class "checkbox1"               
    });
    $('#btnSelectAllRoutes').text("Deselecteer alle trajecten");
    $('#btnSelectAllRoutes').attr("data-checked","true");
}

function deSelectAllRoutes(){
    $('[name=routeId]').each(function() { //loop through each checkbox
        this.checked = false;  //select all checkboxes with class "checkbox1"               
    });
    $('#btnSelectAllRoutes').attr("data-checked","false");
    $('#btnSelectAllRoutes').text("Selecteer alle trajecten");
}

$('#btnSelectAllRoutes').click(function(event) {  //on click 
    if($(this).attr("data-checked") == null || $(this).attr("data-checked") == "false"){
        selectAllRoutes();
    }else{
        deSelectAllRoutes();
    }
});


function setPeriodMultiplicity(multiplicity){
    if(multiplicity === undefined) {
        multiplicity = "single";
    }
    switch(multiplicity){
        case "multi": 
            $("[name=periodtype]").attr("value","multi");            
            break;
        case "single": 
            $("[name=periodtype]").attr("value","single");    
            break;
    }
}


$(".datetimepicker").bootstrapMaterialDatePicker({ 
    format : 'DD MMMM YYYY - HH:mm',
    lang: 'nl',
    weekStart : 1
}).on('change', function(e, date){
    if($(this).attr('id')){
        var hiddenName = $(this).attr('id'); 
        $("[name="+hiddenName+"]").val(new Date(date).getTime());
    }
});
  
$(".datepicker").bootstrapMaterialDatePicker({ 
    format : 'DD MMMM YYYY',
    time: false,
    lang: 'nl',
    weekStart : 1
}).on('change', function(e, date){
    if($(this).attr('id')){
        var hiddenName = $(this).attr('id'); 
        var date = new Date(date);
        date.setMinutes(0);
        date.setHours(0);
        date.setSeconds(0);
        date.setMilliseconds(0);
        $("[name="+hiddenName+"]").val(date.getTime());
    }
});
  
  
$("#availableRoutesList label").mouseover(showRoutePreview)
        .mouseleave(hideRoutePreview)
        .click(addRouteToList);


    
$('.btnRefreshAnalyse').click(function(e) {
    e.preventDefault();
    $('#formRefreshAnalyse').submit(function() {
        $(this).children('[name=periodStartDummy]').remove();
        $(this).children('[name=periodEndDummy]').remove();
    });
});
  
function evaluateNewPeriodForm(){
    newPeriodStart = parseInt($("[name=txtNewPeriodStart]").val());
    newPeriodEnd = parseInt($("[name=txtNewPeriodEnd]").val());
    addPeriodToTable(newPeriodStart,newPeriodEnd);   
};

var form = $("#analyseInitForm");
var formAddNewPeriod = $("#formAddNewPeriod");

$("#btnAddNewPeriod").click(function(event){
    var validator = formAddNewPeriod.validate({
        errorClass: "invalid",
        rules: {
            newPeriodStartDummy: "required",
            newPeriodEndDummy: "required",
        },
        messages: {
            newPeriodStartDummy: "Please enter your firstname",
            newPeriodEndDummy: "Please enter your lastname"
        }
    });
    if(formAddNewPeriod.valid()){
        evaluateNewPeriodForm();
        $('#modelAddNewPeriod').closeModal();
        validator.resetForm();
    }
});

function addPeriodToTable(start, end){
    //btnEdit = $("<a />").attr("href","#!").addClass("btn-floating blue").append($("<i />").addClass("material-icons").text("create"));
    //btnRemove = $("<a />").attr("href","#!").addClass("btn-floating red").click(removeRow).append($("<i />").addClass("material-icons").text("delete"));
    btnEdit = "";
    btnRemove = "";
    newPeriodStart = start;
    newPeriodEnd = end;
    if(addedPeriods === 0 || addedPeriods === null) $("#newPeriodList tbody").html("");
    $("#newPeriodList tbody").append(
            $("<tr />").append(
            $("<td />").text(dateFormat(new Date(newPeriodStart), "dd-mm-yyyy")).append($("<input />").attr("type","hidden").addClass("startTime").val(newPeriodStart))
            ).append(
            $("<td />").text(dateFormat(new Date(newPeriodEnd), "dd-mm-yyyy")).append($("<input />").attr("type","hidden").addClass("endTime").val(newPeriodStart))
            ).append(
            $("<td />").addClass("right-align").append(btnEdit).append(btnRemove)
            ));
    addedPeriods++;
    $("#txtNewPeriodStart").val("");
    $("#txtNewPeriodEnd").val("");
    oldPeriodStart = $("[name=periodsStart]").val();
    oldPeriodEnd = $("[name=periodsEnd]").val();
    
    if(oldPeriodStart !== "") {
        oldPeriodStart = oldPeriodStart+" ";
        oldPeriodEnd = oldPeriodEnd+" ";
    }
    $("[name=periodsStart]").val(oldPeriodStart+""+newPeriodStart);
    $("[name=periodsEnd]").val(oldPeriodEnd+""+newPeriodEnd);
}

removeRow = function(){
   row = $(this).closest("tr");
   //delete start in input
   var old = $("[name=periodsStart]").val();
   var to_remove_start = row.find(".startTime").first().val();
   var to_remove_end = row.find(".endTime").first().val();
   alert(to_remove_start);
   alert(to_remove_end);
   alert($("[name=periodsStart]").val().replaceAll(to_remove_start,""));
   alert($("[name=periodsEnd]").val().replaceAll(to_remove_start,""));
   
   
   //delete end in input
   $("[name=periodsEnd]").val(oldPeriodEnd+""+newPeriodEnd);
   row.remove();
   console.log($(this).parent("tr"));
};


$("#btnSelectRoutes").click(function(){
    /*switch($(this).data("multiplicity")){
        case "multi": 
            for(i=0; i<20; i++){
                 $("#availableRoutesList")
                         .append($("<li />")
                         .append($("<input />").attr("id","route"+i).attr("type","checkbox"))
                         .append($("<label />").attr("for","route"+i).mouseover(showRoutePreview).mouseleave(hideRoutePreview).click(addRouteToList)
                         .append($("<span />").text("R4: Gent - Zelzate")
                         )));
            }
             break;
        case "single": 
             for(i=0; i<20; i++){
                 $("#availableRoutesList")
                         .append($("<li />")
                         .append($("<input />").attr("id","route"+i).attr("type","radio").attr("name","route"))
                         .append($("<label />").attr("for","route"+i).mouseover(showRoutePreview).mouseleave(hideRoutePreview).click(addRouteToList)
                         .append($("<span />").text("R4: Gent - Zelzate")
                         )));
                }
            break;
    }
     */
    $('#selectRoutesModel').openModal();
});

                  


