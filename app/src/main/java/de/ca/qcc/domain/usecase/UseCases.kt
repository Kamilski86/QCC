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

        val isMatch = qr.serial != null && qrNormalized == rfidNormalized

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
                message = when {
                    qr.serial == null -> "QR enthält keine Seriennummer"
                    qrNormalized == null -> "QR-SGTIN konnte nicht aufgebaut werden"
                    else -> "SGTIN stimmt nicht überein"
                }
            )
        }
    }
}
