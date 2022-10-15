Structure Monitoring Tracker (Solution Code)
========================
<h3>Written for NDTS(Natural Disaster Tracking System) project at my internship in Havelsan.</h3>

 Using an accelerometer and gyroscope, the tracker checks whether the building is collapsed or shaking.
 <ul>
<li>Sends sensor data via HTTP to Ubidots. Data is visualized in the dashboard.</li>
<li>Sends and receives data from operator device via Wi-fi direct.</li>
  <li>Uses string based data protocol for sending data to operator's device.</li>
  </ul>
  
Could potentially work with smartwatches to send heartbeat data to Incident Response Center so in the event of a collapse they can quickly get information about the rescue operator's health.
