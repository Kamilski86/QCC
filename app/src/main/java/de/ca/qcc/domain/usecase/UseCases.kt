package de.ca.qcc.domain.usecase

import de.ca.qcc.data.repository.MismatchRepository
import de.ca.qcc.domain.model.ComparisonResult
import javax.inject.Inject
import de.ca.qcc.domain.model.ComparisonStatus
import de.ca.qcc.domain.model.MismatchRecord
import de.ca.qcc.domain.model.QrScanResult
import de.ca.qcc.domain.model.RfidScanResult

class CompareScansUseCase @Inject constructor(
    private val mismatchRepository: MismatchRepository
) {
    suspend operator fun invoke(
        qr: QrScanResult,
        rfid: RfidScanResult
    ): ComparisonResult {
        val qrNormalized = qr.sgtin?.trim()?.lowercase()
        val rfidNormalized = rfid.sgtin.trim().lowercase()

        // Compare GTIN-14 + serial, not the dotted SGTIN strings: where the dots fall depends on the
        // company prefix length, which the QR side has to assume (7 digits) but the EPC encodes
        val rfidSerial = rfidNormalized.substringAfterLast('.')
        val isMatch = qr.serial != null &&
            qr.gtin.trim() == rfid.gtin.trim() &&
            qr.serial.trim().lowercase() == rfidSerial

        mismatchRepository.addScan(
            gtinQr = qr.gtin,
            gtinTag = rfid.gtin,
            isMatch = isMatch,
            sgtinQr = qrNormalized.orEmpty(),
            sgtinTag = rfidNormalized,
            qrRaw = qr.rawValue
        )

        return if (isMatch) {
            ComparisonResult(
                status = ComparisonStatus.MATCH,
                message = "SGTIN stimmt überein"
            )
        } else {
            mismatchRepository.add(
                MismatchRecord(
                    timestamp = System.currentTimeMillis(),
                    epc = rfid.epc,
                    sgtin = rfid.sgtin,
                    gtinTag = rfid.gtin,
                    gtinQr = qr.gtin
                )
            )

            ComparisonResult(
                status = ComparisonStatus.MISMATCH,
                message = if (qr.serial == null) "QR enthält keine Seriennummer" else "SGTIN stimmt nicht überein"
            )
        }
    }
}
