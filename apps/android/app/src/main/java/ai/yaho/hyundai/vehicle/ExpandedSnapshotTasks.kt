
package ai.yaho.hyundai.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.*
import ai.pleos.playground.vehicle.constant.area.*
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit

internal fun expandedSnapshotTasks(vehicle: Vehicle): List<SnapshotTask> {
    val tasks = mutableListOf<SnapshotTask>()

    WindowValue.entries.forEach { part ->
        tasks += SnapshotTask("Window.getSmartGlassConfig.${part.name}") { ok, fail ->
            vehicle.getWindow().checkSmartGlassZoneStateCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(WindowArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getWindow().getSmartGlassConfig(WindowArea(part), onComplete = { config ->
                ok(config?.let { "영역 ${it.zoneCount}개 · 단계 ${it.levelCount}개" })
            }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    tasks += SnapshotTask("Brake.getCurrentBrakePedalStatus") { ok, fail ->
        vehicle.getBrake().checkBrakePedalStatusCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getBrake().getCurrentBrakePedalStatus(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Display.getCurrentTemperatureDisplayUnit") { ok, fail ->
        vehicle.getDisplay().checkTemperatureDisplayUnitCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getDisplay().getCurrentTemperatureDisplayUnit(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    DoorValue.entries.forEach { part ->
        tasks += SnapshotTask("Door.getDoorLockState.${part.name}") { ok, fail ->
            vehicle.getDoor().checkDoorLockCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(DoorArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getDoor().getDoorLockState(DoorArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    tasks += SnapshotTask("Door.getCurrentGloveBoxLockState") { ok, fail ->
        vehicle.getDoor().checkGloveBoxLockCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getDoor().getCurrentGloveBoxLockState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    DoorValue.entries.forEach { part ->
        tasks += SnapshotTask("Door.getCurrentDoorPosition.${part.name}") { ok, fail ->
            vehicle.getDoor().checkDoorPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(DoorArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getDoor().getCurrentDoorPosition(DoorArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    tasks += SnapshotTask("DrivingMode.getCruiseControlTargetSpeed") { ok, fail ->
        vehicle.getDrivingMode().checkCruiseControlTargetSpeedCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getDrivingMode().getCruiseControlTargetSpeed(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("DrivingMode.getCruiseControlState") { ok, fail ->
        vehicle.getDrivingMode().checkCruiseControlStateCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getDrivingMode().getCruiseControlState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("DrivingMode.getCurrentDrivingModeState") { ok, fail ->
        vehicle.getDrivingMode().checkDrivingModeStateCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getDrivingMode().getCurrentDrivingModeState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getInstantaneousEvEfficiency") { ok, fail ->
        vehicle.getEvBattery().checkInstantaneousEvEfficiencyCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getEvBattery().getInstantaneousEvEfficiency(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getSinceChargeEvEfficiency") { ok, fail ->
        vehicle.getEvBattery().checkSinceChargeEvEfficiencyCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getEvBattery().getSinceChargeEvEfficiency(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getCumulativeEvEfficiency") { ok, fail ->
        vehicle.getEvBattery().checkCumulativeEvEfficiencyCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getEvBattery().getCumulativeEvEfficiency(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getCurrentEvEfficiency") { ok, fail ->
        vehicle.getEvBattery().getCurrentEvEfficiency(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getEvBatteryStateOfCharge") { ok, fail ->
        vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getEvBattery().getEvBatteryStateOfCharge(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getEvBatteryLevel") { ok, fail ->
        vehicle.getEvBattery().getEvBatteryLevel(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getEvChargeCurrentDrawLimit") { ok, fail ->
        vehicle.getEvBattery().getEvChargeCurrentDrawLimit(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.isEvChargePortOpen") { ok, fail ->
        vehicle.getEvBattery().checkEvChargePortOpenCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getEvBattery().isEvChargePortOpen(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getEvChargeSwitchState") { ok, fail ->
        vehicle.getEvBattery().checkEvChargeSwitchCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getEvBattery().getEvChargeSwitchState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("EvBattery.getEvBatteryCapacity") { ok, fail ->
        vehicle.getEvBattery().getEvBatteryCapacity(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Fuel.getInstantaneousFuelEconomy") { ok, fail ->
        vehicle.getFuel().checkInstantaneousFuelEconomyCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getFuel().getInstantaneousFuelEconomy(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Fuel.getSinceChargeFuelEconomy") { ok, fail ->
        vehicle.getFuel().checkSinceChargeFuelEconomyCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getFuel().getSinceChargeFuelEconomy(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Fuel.getCumulativeFuelEconomy") { ok, fail ->
        vehicle.getFuel().checkCumulativeFuelEconomyCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getFuel().getCumulativeFuelEconomy(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Fuel.getCurrentFuelEconomy") { ok, fail ->
        vehicle.getFuel().getCurrentFuelEconomy(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Fuel.getFuelPercent") { ok, fail ->
        vehicle.getFuel().checkFuelPercentCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getFuel().getFuelPercent(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Gear.getCurrentGearSelectionState") { ok, fail ->
        vehicle.getGear().getCurrentGearSelectionState(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacAcState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacAcCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacAcState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacAutoState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacAutoCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacAutoState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacAutoRecirculationState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacAutoRecirculationCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacAutoRecirculationState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    WindowValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacDefrosterState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacDefrosterCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(WindowArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacDefrosterState(WindowArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacDualState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacDualCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacDualState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacFanDirection.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacFanDirectionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacFanDirection(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacFanSpeed.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacFanSpeedCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacFanSpeed(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacMaxAcState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacMaxAcCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacMaxAcState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacMaxDefrostState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacMaxDefrostCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacMaxDefrostState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacPowerState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacPowerCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacPowerState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacRecirculationState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacRecirculationCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacRecirculationState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacAirCleanerState.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacAirCleanerCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacAirCleanerState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacTemperature.${part.name}", unit = "CELSIUS") { ok, fail ->
            vehicle.getHVAC().checkHvacTemperatureCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacTemperature(SeatArea(part), TemperatureUnit.CELSIUS, onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("HVAC.getHvacTemperatureCurrent.${part.name}") { ok, fail ->
            vehicle.getHVAC().checkHvacTemperatureCurrentCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getHVAC().getHvacTemperatureCurrent(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Light.getReadingLightSwitch.${part.name}") { ok, fail ->
            vehicle.getLight().checkReadingLightSwitchCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getLight().getReadingLightSwitch(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Light.getCurrentReadingLightState.${part.name}") { ok, fail ->
            vehicle.getLight().checkReadingLightStateCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getLight().getCurrentReadingLightState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    tasks += SnapshotTask("Light.getCabinLightSwitch") { ok, fail ->
        vehicle.getLight().getCabinLightSwitch(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getCurrentCabinLightState") { ok, fail ->
        vehicle.getLight().getCurrentCabinLightState(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getCurrentHighBeamLightState") { ok, fail ->
        vehicle.getLight().getCurrentHighBeamLightState(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getHighBeamLightSwitch") { ok, fail ->
        vehicle.getLight().getHighBeamLightSwitch(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getLowBeamLightSwitch") { ok, fail ->
        vehicle.getLight().getLowBeamLightSwitch(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getCurrentLowBeamLightState") { ok, fail ->
        vehicle.getLight().getCurrentLowBeamLightState(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getCurrentTailLampLightState") { ok, fail ->
        vehicle.getLight().getCurrentTailLampLightState(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getTailLampLightSwitch") { ok, fail ->
        vehicle.getLight().getTailLampLightSwitch(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getCurrentFrontFogLightState") { ok, fail ->
        vehicle.getLight().checkFrontFogLightStateCapabilities(onComplete = { config, _ ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
                vehicle.getLight().getCurrentFrontFogLightState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getFrontFogLightSwitch") { ok, fail ->
        vehicle.getLight().checkFrontFogLightSwitchCapabilities(onComplete = { config, _ ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
                vehicle.getLight().getFrontFogLightSwitch(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getCurrentRearFogLightState") { ok, fail ->
        vehicle.getLight().checkRearFogLightStateCapabilities(onComplete = { config, _ ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
                vehicle.getLight().getCurrentRearFogLightState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Light.getRearFogLightSwitch") { ok, fail ->
        vehicle.getLight().checkRearFogLightSwitchCapabilities(onComplete = { config, _ ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
                vehicle.getLight().getRearFogLightSwitch(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Light.getAmbientLightBrightness.${part.name}") { ok, fail ->
            vehicle.getLight().checkAmbientLightBrightnessCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getLight().getAmbientLightBrightness(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Light.getAmbientLightBrightnessColor.${part.name}") { ok, fail ->
            vehicle.getLight().checkAmbientLightBrightnessColorCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getLight().getAmbientLightBrightnessColor(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Light.getAmbientLightSwitch.${part.name}") { ok, fail ->
            vehicle.getLight().checkAmbientLightSwitchCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getLight().getAmbientLightSwitch(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Light.getAmbientLightState.${part.name}") { ok, fail ->
            vehicle.getLight().checkAmbientLightStateCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getLight().getAmbientLightState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    tasks += SnapshotTask("Light.getAmbientColorAutoByDriveModeEnabled") { ok, fail ->
        vehicle.getLight().checkAmbientColorAutoByDriveModeEnabledCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getLight().getAmbientColorAutoByDriveModeEnabled(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getCurrentDistanceDriven") { ok, fail ->
        vehicle.getOdometer().getCurrentDistanceDriven(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getTripCurrentDrivingTime") { ok, fail ->
        vehicle.getOdometer().checkTripCurrentDrivingTimeCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getOdometer().getTripCurrentDrivingTime(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getTripCumulativeDrivingTime") { ok, fail ->
        vehicle.getOdometer().checkTripCumulativeDrivingTimeCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getOdometer().getTripCumulativeDrivingTime(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getTripSinceRefillDrivingTime") { ok, fail ->
        vehicle.getOdometer().checkTripSinceRefillDrivingTimeCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getOdometer().getTripSinceRefillDrivingTime(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getTripCurrentDrivingDistance") { ok, fail ->
        vehicle.getOdometer().checkTripCurrentDrivingDistanceCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getOdometer().getTripCurrentDrivingDistance(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getTripInstantaneousDrivingDistance") { ok, fail ->
        vehicle.getOdometer().checkTripInstantaneousDrivingDistanceCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getOdometer().getTripInstantaneousDrivingDistance(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getTripCumulativeDrivingDistance") { ok, fail ->
        vehicle.getOdometer().checkTripCumulativeDrivingDistanceCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getOdometer().getTripCumulativeDrivingDistance(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getTripSinceRefillDrivingDistance") { ok, fail ->
        vehicle.getOdometer().checkTripSinceRefillDrivingDistanceCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getOdometer().getTripSinceRefillDrivingDistance(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Odometer.getRangeRemainingDistance") { ok, fail ->
        vehicle.getOdometer().getRangeRemainingDistance(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Safety.getEngineOilLevelState") { ok, fail ->
        vehicle.getSafety().checkEngineOilLevelStateCapabilities(onComplete = { config, _ ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
                vehicle.getSafety().getEngineOilLevelState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Safety.getBrakeFluidLevelState") { ok, fail ->
        vehicle.getSafety().checkBrakeFluidLevelStateCapabilities(onComplete = { config, _ ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
                vehicle.getSafety().getBrakeFluidLevelState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Safety.getTransmissionOilLevelState") { ok, fail ->
        vehicle.getSafety().checkTransmissionOilLevelStateCapabilities(onComplete = { config, _ ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
                vehicle.getSafety().getTransmissionOilLevelState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("Safety.hasLowWasherFluidWarning") { ok, fail ->
        vehicle.getSafety().checkLowWasherFluidWarningStateCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getSafety().hasLowWasherFluidWarning(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Safety.hasSeatAirbagBeenDeployed.${part.name}") { ok, fail ->
            vehicle.getSafety().checkAirbagDeployedCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSafety().hasSeatAirbagBeenDeployed(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    tasks += SnapshotTask("Safety.hasPowerDownWarningIndicator") { ok, fail ->
        vehicle.getSafety().checkPowerDownWarningIndicatorCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getSafety().hasPowerDownWarningIndicator(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Safety.hasSeatAirbagEnabled.${part.name}") { ok, fail ->
            vehicle.getSafety().checkAirbagCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSafety().hasSeatAirbagEnabled(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Safety.hasSeatBeltNotBuckledWarning.${part.name}") { ok, fail ->
            vehicle.getSafety().checkSeatNotBuckledWarningCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSafety().hasSeatBeltNotBuckledWarning(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatOccupancy.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatOccupancyCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatOccupancy(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatHeadrestForeAftPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatHeadrestForeAftPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatHeadrestForeAftPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatHeadrestHeightAftPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatHeadrestHeightPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatHeadrestHeightAftPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatHeightPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatHeightPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatHeightPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatLumbarForeAftPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatLumbarForeAftPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatLumbarForeAftPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatLumbarSideSupportPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatLumbarSideSupportPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatLumbarSideSupportPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatTiltPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatTiltPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatTiltPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatDepthPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatDepthPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatDepthPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatForeAftPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatForeAftPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatForeAftPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatHeadrestAnglePosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatHeadrestAnglePositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatHeadrestAnglePosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatBackrestAnglePosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatBackrestAnglePositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatBackrestAnglePosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatBeltBuckledState.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatBeltBuckledCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatBeltBuckledState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getCurrentSeatBeltHeightPosition.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatBeltHeightPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getCurrentSeatBeltHeightPosition(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getHvacSeatTemperature.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatTemperatureCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getHvacSeatTemperature(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    SeatValue.entries.forEach { part ->
        tasks += SnapshotTask("Seat.getHvacSeatVentilationState.${part.name}") { ok, fail ->
            vehicle.getSeat().checkSeatVentilationCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(SeatArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSeat().getHvacSeatVentilationState(SeatArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    MirrorValue.entries.forEach { part ->
        tasks += SnapshotTask("SideMirror.getHvacSideMirrorHeatState.${part.name}") { ok, fail ->
            vehicle.getSideMirror().checkHvacSideMirrorHeatCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(MirrorArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getSideMirror().getHvacSideMirrorHeatState(MirrorArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    tasks += SnapshotTask("SteeringWheel.getHvacSteeringWheelHeatState") { ok, fail ->
        vehicle.getSteeringWheel().getHvacSteeringWheelHeatState(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("SteeringWheel.getCurrentSteeringWheelAngle") { ok, fail ->
        vehicle.getSteeringWheel().getCurrentSteeringWheelAngle(onComplete = { ok(it) }, onFailed = { fail(it) })
    }
    WheelValue.entries.filter { it != WheelValue.UNKNOWN }.forEach { part ->
        tasks += SnapshotTask("Tire.getLowTirePressure.${part.name}") { ok, fail ->
            vehicle.getTire().checkLowTirePressureCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(WheelArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getTire().getLowTirePressure(WheelArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }

    tasks += SnapshotTask("Toll.getCurrentEtcsVoiceGuidanceVolume") { ok, fail ->
        vehicle.getToll().checkEtcsVoiceGuidanceVolumeCapabilities(onComplete = { config, _ ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
                vehicle.getToll().getCurrentEtcsVoiceGuidanceVolume(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    tasks += SnapshotTask("TurnSignal.getCurrentTurnSignalState") { ok, fail ->
        vehicle.getTurnSignal().checkTurnSignalStateCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getTurnSignal().getCurrentTurnSignalState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    WindowValue.entries.forEach { part ->
        tasks += SnapshotTask("Window.getCurrentWindowLockState.${part.name}") { ok, fail ->
            vehicle.getWindow().checkWindowLockCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.contains(WindowArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getWindow().getCurrentWindowLockState(WindowArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    WindowValue.entries.forEach { part ->
        tasks += SnapshotTask("Window.getCurrentWindowPosition.${part.name}") { ok, fail ->
            vehicle.getWindow().checkWindowPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(WindowArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getWindow().getCurrentWindowPosition(WindowArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    WindowValue.entries.forEach { part ->
        tasks += SnapshotTask("Window.getCurrentWindowBlindPosition.${part.name}") { ok, fail ->
            vehicle.getWindow().checkWindowBlindPositionCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(WindowArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getWindow().getCurrentWindowBlindPosition(WindowArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    tasks += SnapshotTask("Window.getCurrentSmartGlassState") { ok, fail ->
        vehicle.getWindow().checkSmartGlassStateCapabilities(onComplete = { config ->
            if (config.accessType.name in setOf("READ", "READ_WRITE")) {
        vehicle.getWindow().getCurrentSmartGlassState(onComplete = { ok(it) }, onFailed = { fail(it) })
            } else fail(SignalAccessException(config.accessType.name))
        }, onFailed = { fail(it) })
    }
    WindowValue.entries.forEach { part ->
        tasks += SnapshotTask("Window.getCurrentSmartGlassLevel.${part.name}") { ok, fail ->
            vehicle.getWindow().checkSmartGlassLevelCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(WindowArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getWindow().getCurrentSmartGlassLevel(WindowArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    WindowValue.entries.forEach { part ->
        tasks += SnapshotTask("Wiper.getCurrentWiperState.${part.name}") { ok, fail ->
            vehicle.getWiper().checkWiperStateCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(WindowArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getWiper().getCurrentWiperState(WindowArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    WindowValue.entries.forEach { part ->
        tasks += SnapshotTask("Wiper.getCurrentWiperSwitch.${part.name}") { ok, fail ->
            vehicle.getWiper().checkWiperSwitchCapabilities(onComplete = { config, areas ->
                when {
                    config.accessType.name !in setOf("READ", "READ_WRITE") -> fail(SignalAccessException(config.accessType.name))
                    !areas.containsKey(WindowArea(part)) -> fail(UnsupportedSignalAreaException())
                    else -> {
            vehicle.getWiper().getCurrentWiperSwitch(WindowArea(part), onComplete = { ok(it) }, onFailed = { fail(it) })
                    }
                }
            }, onFailed = { fail(it) })
        }
    }
    return tasks
}
