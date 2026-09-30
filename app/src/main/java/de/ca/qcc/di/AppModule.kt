package de.ca.qcc.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.ca.qcc.data.export.CsvDocumentWriter
import de.ca.qcc.data.export.MediaStoreCsvDocumentWriter
import de.ca.qcc.data.local.AppDatabase
import de.ca.qcc.data.local.MIGRATION_1_2
import de.ca.qcc.data.local.MIGRATION_2_3
import de.ca.qcc.data.local.MIGRATION_3_4
import de.ca.qcc.data.local.MIGRATION_4_5
import de.ca.qcc.data.local.MIGRATION_5_6
import de.ca.qcc.data.local.MIGRATION_6_7
import de.ca.qcc.data.repository.MismatchRepository
import de.ca.qcc.device.honeywell.BarcodeScannerGateway
import de.ca.qcc.device.honeywell.HoneywellCt37ScannerGateway
import de.ca.qcc.device.zebra.RfidReaderGateway
import de.ca.qcc.device.zebra.ZebraRfd8500Gateway
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "qcc.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    @Singleton
    fun provideMismatchRepository(db: AppDatabase): MismatchRepository =
        MismatchRepository(db)

    @Provides
    fun provideCsvDocumentWriter(@ApplicationContext context: Context): CsvDocumentWriter =
        MediaStoreCsvDocumentWriter(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GatewayModule {

    @Binds
    @Singleton
    abstract fun bindBarcodeScanner(impl: HoneywellCt37ScannerGateway): BarcodeScannerGateway

    @Binds
    @Singleton
    abstract fun bindRfidReader(impl: ZebraRfd8500Gateway): RfidReaderGateway
}