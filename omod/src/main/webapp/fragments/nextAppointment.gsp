<script>

    var urlParams = new URLSearchParams(window.location.search);

    jq(document).ready(function () {
        getNextAppointemt(urlParams.get("patientId"))
    });

    function getNextAppointemt(patient) {
        jq.ajax({
            type: "GET",
            url: '/' + OPENMRS_CONTEXT_PATH + "/ws/rest/v1/obs?limit=1&v=full&patient=" + patient + "&concept=dcac04cf-30ab-102d-86b0-7a5022ba4115&startIndex=1",
            dataType: "json",
            contentType: "application/json",
            async: false,
            success: function (data) {
                var appointment = data.results;

                if (appointment.length > 0) {
                    jq('#appointment_location').html("&nbsp;&nbsp;"+appointment[0].location.display)
                    jq('#appointment_date').html("&nbsp;&nbsp;"+formatDateForDisplay(new Date(appointment[0].value)))
                }

            }
        });
    }

</script>


<div class="info-section patientsummary">
    <div class="info-header">
        <i class=" icon-user-md"></i>
        <h3>NEXT APPOINTMENT</h3>
    </div>
    <div class="info-body">
        <div style="display: block; overflow: hidden; padding-right: 5px; padding-bottom: 2px">
            <span style="float: left" class="ng-binding">
               DATE:
            </span>
            <!-- ngIf: obs.groupMembers != null && obs.groupMembers != undefined && obs.groupMembers.length > 0 -->
            <span style="float: left">
                <strong id="appointment_date" class="ng-binding"></strong>
            </span>
        </div>

        <div style="display: block; overflow: hidden; padding-right: 5px; padding-bottom: 2px">
            <span style="float: left" class="ng-binding">
                LOCATION:
            </span>
            <!-- ngIf: obs.groupMembers != null && obs.groupMembers != undefined && obs.groupMembers.length > 0 -->
            <span style="float: left">
                <strong id="appointment_location" class="ng-binding"></strong>
            </span>
        </div>
    </div>
</div>