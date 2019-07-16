package com.kyberswap.android.data.repository

import com.kyberswap.android.data.db.ContactDao
import com.kyberswap.android.domain.model.Contact
import com.kyberswap.android.domain.repository.ContactRepository
import com.kyberswap.android.domain.usecase.contact.DeleteContactUseCase
import com.kyberswap.android.domain.usecase.contact.GetContactUseCase
import com.kyberswap.android.domain.usecase.contact.SaveContactUseCase
import com.kyberswap.android.presentation.common.DEFAULT_NAME
import io.reactivex.Completable
import io.reactivex.Flowable
import javax.inject.Inject


class ContactDataRepository @Inject constructor(
    private val contactDao: ContactDao

) : ContactRepository {

    override fun saveContact(param: SaveContactUseCase.Param): Completable {
        return Completable.fromCallable {
            val name = if (param.name.isEmpty()) {
                DEFAULT_NAME
     else param.name

            val findContactByAddress = contactDao.findContactByAddress(param.address)
            val updatedAt = System.currentTimeMillis() / 1000
            val contact = findContactByAddress?.copy(
                walletAddress = param.walletAddress,
                address = param.address,
                name = param.name,
                updatedAt = updatedAt
            ) ?: Contact(param.walletAddress, param.address, name, updatedAt)
            contactDao.insertContact(contact)

    }

    override fun getContacts(param: GetContactUseCase.Param): Flowable<List<Contact>> {
        return contactDao.loadContactByWalletAddress(param.walletAddress).map { contacts ->
            contacts.sortedByDescending { it.updatedAt }

    }

    override fun deleteContact(param: DeleteContactUseCase.Param): Completable {
        return contactDao.deleteContactCompletable(param.contact)
    }

}