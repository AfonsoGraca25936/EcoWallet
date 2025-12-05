package pt.ipt.dam.ecowallet.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import pt.ipt.dam.ecowallet.model.Despesa
import pt.ipt.dam.ecowallet.model.User

//Usa o singleton para a app so abrir a base de dados uma vez

// 1. Definimos quais são as tabelas (Entities) e a versão da BD
@Database(entities = [Despesa::class, User::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // 2. Os DAOs que criámos antes
    abstract fun despesaDao(): DespesaDao
    abstract fun utilizadorDao(): UserDao

    // 3. O Singleton (Código padrão para evitar abrir 2 bases de dados ao mesmo tempo)
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ecowallet_database" // Nome do ficheiro da BD no telemóvel
                ).fallbackToDestructiveMigration() // Se mudares a BD, ele recria-a (útil em dev)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}