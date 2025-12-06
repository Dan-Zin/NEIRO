package com.example.neirotech

import com.neurosdk2.neuro.types.SensorInfo

/**
 * Менеджер состояния подключения BrainBit.
 * Хранит информацию о найденном устройстве (SensorInfo) из SDK2,
 * а также статус подключения, имя и адрес устройства.
 * 
 * SensorInfo хранится для повторного использования при создании Sensor
 * в разных Activity без необходимости повторного сканирования.
 */
object ConnectionManager {
    @Volatile
    private var connected = false
    @Volatile
    private var deviceName: String? = null
    @Volatile
    private var deviceAddress: String? = null
    @Volatile
    private var sensorInfo: SensorInfo? = null

    /**
     * Устанавливает состояние подключения с информацией об устройстве.
     * @param name имя устройства
     * @param address MAC-адрес устройства
     * @param info SensorInfo из SDK2 Scanner (опционально)
     */
    fun setConnected(name: String?, address: String?, info: SensorInfo? = null) {
        connected = true
        deviceName = name
        deviceAddress = address
        sensorInfo = info
    }

    /**
     * Сохраняет SensorInfo без изменения статуса подключения.
     * Используется при сканировании для сохранения информации о найденном устройстве.
     */
    fun setSensorInfo(info: SensorInfo?) {
        sensorInfo = info
        if (info != null) {
            deviceName = info.name
            deviceAddress = info.address
        }
    }

    fun clear() {
        connected = false
        deviceName = null
        deviceAddress = null
        sensorInfo = null
    }

    fun isConnected(): Boolean = connected

    fun getName(): String? = deviceName
    fun getAddress(): String? = deviceAddress
    
    /**
     * Возвращает сохранённый SensorInfo для создания Sensor в другой Activity.
     * Может быть null, если устройство было найдено через обычный BLE scan.
     */
    fun getSensorInfo(): SensorInfo? = sensorInfo
    
    /**
     * Проверяет, есть ли сохранённая информация о сенсоре.
     */
    fun hasSensorInfo(): Boolean = sensorInfo != null
}

