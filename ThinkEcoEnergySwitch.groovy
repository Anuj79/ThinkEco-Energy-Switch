/*
*	Think Eco Energy Switch
*
*  Copyright 2017 Elastic Development
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*  The latest version of this file can be found at:
*  https://github.com/jpansarasa/SmartThings/blob/master/DeviceTypes/AeotecHDSS.groovy
*
*  Revision History
*  ----------------
*
*  2017-10-21: Version: 1.0.0
*  Initial Revision
*
*/
metadata {
  definition (name: “ThinkEco Energy Switch", namespace: "elasticdev", author: “Anuj H“) {
capability "Switch"
capability "Energy Meter"
capability "Power Meter"
capability "Configuration"
capability "Sensor"
capability "Actuator"
capability "Polling"
capability "Refresh"

command "reset"

fingerprint deviceId: "0x1001", inClusters: "0x25, 0x31, 0x32, 0x27, 0x70, 0x85, 0x72, 0x86, 0xEF, 0x82"
  }

  // simulator metadata
  simulator {
status "on":  "command: 2003, payload: FF"
status "off": "command: 2003, payload: 00"

for (int i = 0; i <= 10000; i += 1000) {
    status "power  ${i} W":
      new physicalgraph.zwave.Zwave().meterV1.meterReport(scaledMeterValue: i, precision: 3, meterType: 4, scale: 2, size: 4).incomingMessage()
}
for (int i = 0; i <= 100; i += 10) {
    status "energy  ${i} kWh":
      new physicalgraph.zwave.Zwave().meterV1.meterReport(scaledMeterValue: i, precision: 3, meterType: 0, scale: 0, size: 4).incomingMessage()
}

// reply messages
reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
reply "200100,delay 100,2502": "command: 2503, payload: 00"
  }

  // tile definitions
  tiles {
standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
    state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
    state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
}
valueTile("power", "device.power", decoration: "flat") {
    state "default", label:'${currentValue} W'
}
valueTile("energy", "device.energy", decoration: "flat") {
    state "default", label:'${currentValue} kWh'
}
standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat") {
    state "default", label:'reset kWh', action:"reset", icon:"st.secondary.refresh-icon"
}
standardTile("configure", "device.power", inactiveLabel: false, decoration: "flat") {
    state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
}
standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat") {
    state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
}

main (["switch","energy","power"])
details(["switch","power","energy","reset","configure","refresh"])
  }

  preferences {
      input("username", "string", title:”ThinkEco Username", description: "ThinkEco Username", required: true, displayDuringSetup: true)
      input("password", "password", title:”ThinkEco Password", description: "ThinkEco Password", required: true, displayDuringSetup: true)
      input "disableOnOff", "boolean",
              title: "Disable On/Off?",
              defaultValue: false,
              displayDuringSetup: true
      input "reportInterval", "number",
              title: "Report Interval",
              description: "The time interval in minutes for sending device reports",
              defaultValue: 1,
              required: false,
              displayDuringSetup: true
input "displayEvents", "boolean",
  title: "Display power events in the Activity log? ",
  defaultValue: true,
  displayDuringSetup: true
      input "switchAll", "enum",
              title: "Respond to switch all?",
              description: "How does the switch respond to the 'Switch All' command",
              options:["Disabled", "Off Enabled", "On Enabled", "On And Off Enabled"],
              defaultValue: "On And Off Enabled",
              required:false,
              displayDuringSetup: true
      input "debugOutput", "boolean",
              title: "Enable debug logging?",
              defaultValue: false,
              displayDuringSetup: true

  }
}

/********************************************************************************
*	Methods																		*
********************************************************************************/


def api(method, args = [], success = {}) {
log.info "Executing 'api'"

if(!isLoggedIn()) {
  log.debug "Need to login"
  login(method, args, success)
  return
}

// SimpliSafe requires this funkiness
def existing_args = args
def required_payload = [
  no_persist: 0,
  XDEBUG_SESSION_START: 'session_name',
]

// append it to the args
if (existing_args != [])
  {
  args = existing_args + required_payload
  }
  else {
  args = required_payload
  }

def methods = [
  'locations': [uri: "https://simplisafe.com/mobile/$state.auth.uid/locations", type: 'post'],
  'status': [uri: "https://simplisafe.com/mobile/$state.auth.uid/sid/$state.locationID/dashboard", type: 'post'],
  'events': [uri: "https://simplisafe.com/mobile/$state.auth.uid/sid/$state.locationID/events", type: 'post'],
  'get-state': [uri: "https://simplisafe.com/mobile/$state.auth.uid/sid/$state.locationID/get-state", type: 'post'],
  'set-state': [uri: "https://simplisafe.com/mobile/$state.auth.uid/sid/$state.locationID/set-state", type: 'post'],
  'update-freeze': [uri: "https://simplisafe.com/account2/$state.auth.uid/sid/$state.locationID/control-panel/utility/update-freeze", type: 'post'],
  'logout': [uri: "https://simplisafe.com/mobile/logout", type: 'post']
]

def request = methods.getAt(method)

log.debug "Starting $method : $args"
doRequest(request.uri, args, request.type, success)
}

// Need to be logged in before this is called. So don't call this. Call api.
def doRequest(uri, args, type, success) {
log.debug "Calling $type : $uri : $args"

def params = [
  uri: uri,
  headers: [
    'Cookie': state.cookiess
  ],
  body: args
]

//	log.trace params

try {
  if (type == 'post') {
    httpPost(params, success)
  } else if (type == 'get') {
    httpGet(params, success)
  }

} catch (e) {
  log.debug "something went wrong: $e"
}
}

def login(method = null, args = [], success = {}) {
log.info "Executing 'login'"
def params = [
  uri: 'https://web.mymodlet.com/Account/Login',
  body: [
    Email: settings.username,
    Password: settings.password,
    version: 1200,
    no_persist: 1,
    XDEBUG_SESSION_START: 'session_name'
  ]
]

state.cookiess = ''

httpPost(params) {response ->
//	log.trace "Login response, $response.status $response.data"
//	log.trace response.headers

  state.auth = response.data

  // set the expiration to 10 minutes
  state.auth.expires_at = new Date().getTime() + 600000;

  response.getHeaders('Set-Cookie').each {
    String cookie = it.value.split(';|,')[0]
  //	log.trace "Adding cookie to collection: $cookie"
    state.cookiess = state.cookiess+cookie+';'
  }
//	log.trace "cookies: $state.cookiess"

  // get location ID
  locations()

  api(method, args, success)

}
}

/**
*  updated - Called when the preferences of the device type are changed
*/
def updated() {
  state.onOffDisabled = ("true" == disableOnOff)
  state.display = ("true" == displayEvents)
  state.debug = ("true" == debugOutput)
  if (state.debug) log.debug "updated(disableOnOff: ${disableOnOff}(${state.onOffDisabled}), reportInterval: ${reportInterval}, displayEvents: ${displayEvents}, switchAll: ${switchAll}, debugOutput: ${debugOutput}(${state.debug}))"
  response(configure())
}

/**
*  parse - Called when messages from a device are received from the hub
*
*  The parse method is responsible for interpreting those messages and returning Event definitions.
*
*  String	description		The message from the device
*/
def parse(String description) {
  if (state.debug) log.debug "Parse(description: \"${description}\")"

  def event = null

  // The first parameter is the description string
  // The second parameter is a map that specifies the version of each command to use
  def cmd = zwave.parse(description, [0x20 : 1, 0x25 : 1, 0x31 : 5, 0x32 : 3, 0x27 : 1, 0x70 : 1, 0x85 : 2, 0x72 : 2, 0x86 : 2, 0x82 : 1])

  if (cmd) {
      event = createEvent(zwaveEvent(cmd))
  }
  if (state.debug) log.debug "Parse returned ${event?.inspect()}"
  return event
}

/**
*  on - Turns on the switch
*
*  Required for the "Switch" capability
*/
def on() {
  if (state.onOffDisabled) {
      if (state.debug) log.debug "On/Off disabled"

  }
  else {

  }
}

/**
*  off - Turns off the switch
*
*  Required for the "Switch" capability
*/
def off() {
  if (state.onOffDisabled) {
      if (state.debug) log.debug "On/Off disabled"

  }
  else {

  }
}

/**
*  poll - Polls the device
*
*  Required for the "Polling" capability
*/
def poll() {

}

/**
*  refresh - Refreshed values from the device
*
*  Required for the "Refresh" capability

def refresh() {
  delayBetween([
zwave.basicV1.basicGet().format(),
zwave.switchBinaryV1.switchBinaryGet().format(),
zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 4, scale: 0).format(),
zwave.meterV3.meterGet(scale: 0).format(),	//kWh
zwave.meterV3.meterGet(scale: 2).format()	//Wattage
  ])
}

/**
*  reset - Resets the devices energy usage meter
*
*  Defined by the custom command "reset"

def reset() {
}

/**
*  configure - Configures the parameters of the device
*
*  Required for the "Configuration" capability

def configure() {
  //Get the values from the preferences section
  def reportIntervalSecs = 60;
  if (reportInterval) {
reportIntervalSecs = 60 * reportInterval.toInteger()
  }

}

/********************************************************************************
*	Event Handlers																*
********************************************************************************/

/**
*   Default event handler -  Called for all unhandled events
*/
def zwaveEvent(physicalgraph.zwave.Command cmd) {    if (state.debug) {
      log.debug "Unhandled: $cmd"
      createEvent(descriptionText: "${device.displayName}: ${cmd}")
  }
  else {
      [:]
  }
}

/**
*  COMMAND_CLASS_BASIC (0x20)
*
*  Short	value	0xFF for on, 0x00 for off

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
  if (state.debug) log.debug "BasicSet(value:${cmd.value})"
  createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical", displayed: true, isStateChange: true)
}

/**
*  COMMAND_CLASS_BASIC (0x20)
*
*  Short	value	0xFF for on, 0x00 for off

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
  if (state.debug) log.debug "BasicReport(value:${cmd.value})"
  createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

/**
*  COMMAND_CLASS_SWITCH_BINARY (0x25)
*
*  Short	value	0xFF for on, 0x00 for off

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinarySet cmd)
{
  if (state.debug) log.debug "SwitchBinarySet(value:${cmd.value})"
  createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital", displayed: true, isStateChange: true)
}

/**
*  COMMAND_CLASS_SWITCH_BINARY (0x25)
*
*  Short	value	0xFF for on, 0x00 for off

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
  if (state.debug) log.debug "SwitchBinaryReport(value:${cmd.value})"
  createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
}

/**
*  COMMAND_CLASS_METER (0x32)
*
*  Integer	deltaTime		    Time in seconds since last report
*  Short	meterType		    Unknown = 0, Electric = 1, Gas = 2, Water = 3
*  List<Short>	meterValue		    Meter value as an array of bytes
*  Double	scaledMeterValue	    Meter value as a double
*  List<Short>	previousMeterValue	    Previous meter value as an array of bytes
*  Double	scaledPreviousMeterValue    Previous meter value as a double
*  Short	size			    The size of the array for the meterValue and previousMeterValue
*  Short	scale			    The scale of the values: "kWh"=0, "kVAh"=1, "Watts"=2, "pulses"=3, "Volts"=4, "Amps"=5, "Power Factor"=6, "Unknown"=7
*  Short	precision		    The decimal precision of the values
*  Short	rateType		    ???
*  Boolean	scale2			    ???

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd) {
  def meterTypes = ["Unknown", "Electric", "Gas", "Water"]
  def electricNames = ["energy", "energy", "power", "count",  "voltage", "current", "powerFactor",  "unknown"]
  def electricUnits = ["kWh",    "kVAh",   "W",     "pulses", "V",       "A",       "Power Factor", ""]

  if (state.debug) log.debug "MeterReport(deltaTime:${cmd.deltaTime} secs, meterType:${meterTypes[cmd.meterType]}, meterValue:${cmd.scaledMeterValue}, previousMeterValue:${cmd.scaledPreviousMeterValue}, scale:${electricNames[cmd.scale]}(${cmd.scale}), precision:${cmd.precision}, rateType:${cmd.rateType})"

  //NOTE ScaledPreviousMeterValue does not always contain a value
  def previousValue = cmd.scaledPreviousMeterValue ?: 0

  def map = [ name: electricNames[cmd.scale], unit: electricUnits[cmd.scale], displayed: state.display]
  switch(cmd.scale) {
      case 0: //kWh
    previousValue = device.currentValue("energy") ?: cmd.scaledPreviousMeterValue ?: 0
          map.value = cmd.scaledMeterValue
          break;
      case 1: //kVAh
          map.value = cmd.scaledMeterValue
          break;
      case 2: //Watts
          previousValue = device.currentValue("power") ?: cmd.scaledPreviousMeterValue ?: 0
          map.value = Math.round(cmd.scaledMeterValue)
          break;
      case 3: //pulses
          map.value = Math.round(cmd.scaledMeterValue)
          break;
      case 4: //Volts
          previousValue = device.currentValue("voltage") ?: cmd.scaledPreviousMeterValue ?: 0
          map.value = cmd.scaledMeterValue
          break;
      case 5: //Amps
          previousValue = device.currentValue("current") ?: cmd.scaledPreviousMeterValue ?: 0
          map.value = cmd.scaledMeterValue
          break;
      case 6: //Power Factor
      case 7: //Unknown
          map.value = cmd.scaledMeterValue
          break;
      default:
          break;
  }
  //Check if the value has changed my more than 5%, if so mark as a stateChange.
  // Changed it to .01 so for it to update more quicckly
  map.isStateChange = ((cmd.scaledMeterValue - previousValue).abs() > (cmd.scaledMeterValue * 0.001))

  createEvent(map)
}
⁃	 212 444 0443
/**
*  COMMAND_CLASS_SENSOR_MULTILEVEL  (0x31)
*
*  Short	sensorType	Supported Sensor: 0x04 (power Sensor)
*  Short	scale		Supported scale:  0x00 (W) and 0x01 (BTU/h)

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
  if (state.debug) log.debug "SensorMultilevelReport(sensorType:${cmd.sensorType}, scale:${cmd.scale}, precision:${cmd.precision}, scaledSensorValue:${cmd.scaledSensorValue}, sensorValue:${cmd.sensorValue}, size:${cmd.size})"

  def map = [ value: cmd.scaledSensorValue, displayed: false]
  switch(cmd.sensorType) {
      case physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport.SENSOR_TYPE_POWER_VERSION_2: 	// 4
          map.name = "power"
          map.unit = cmd.scale ? "BTU/h" : "W"
          map.value = Math.round(cmd.scaledSensorValue)
          break;
      case physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport.SENSOR_TYPE_VOLTAGE_VERSION_3:	// 15
          map.name = "voltage"
          map.unit = cmd.scale ? "mV" : "V"
          break;
      case physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport.SENSOR_TYPE_CURRENT_VERSION_3:	// 16
          map.name = "current"
          map.unit = cmd.scale ? "mA" : "A"
          break;
      default:
          map.name = "unknown sensor ($cmd.sensorType)"
          break;
  }
  createEvent(map)
}
*/
//EOF
