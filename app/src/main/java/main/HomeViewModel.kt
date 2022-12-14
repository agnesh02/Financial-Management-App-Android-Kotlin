package main

import android.app.Application
import android.app.DatePickerDialog
import android.widget.ArrayAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.orhanobut.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import repositories.AuthenticationRepository
import repositories.ExpenditureRepository
import repositories.FinanceRepository
import utils.Common
import utils.Common.auth
import utils.Common.setUpLogger
import utils.Common.toastShort
import java.text.SimpleDateFormat
import java.util.*

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    var purpose = ""
    var payee = ""
    var amount = ""
    var errMsg = ""
    private var paymentModeList = arrayListOf("Cash","Debit Card","Digital Wallet","Credit Card","Bank")
    var paymentModeAdapter = ArrayAdapter(getApplication(),android.R.layout.simple_list_item_1,paymentModeList)
    private val expenditureRepository = ExpenditureRepository(auth.currentUser?.email!!)
    private val financeRepository = FinanceRepository(auth.currentUser?.email!!)
    private val _stateFlow = MutableStateFlow(0)
    val stateFlow = _stateFlow.asStateFlow()
    var dateText = MutableLiveData("Select Date")
    val displayInWallet = MutableLiveData("₹0.0")
    val displayInDigitalWallet = MutableLiveData("₹0.0")
    var pBarVisibility = MutableStateFlow(true)
    var liveMsg = MutableStateFlow("")

    var cal: Calendar = Calendar.getInstance()
    val dateSetListener = DatePickerDialog.OnDateSetListener { _ , year, monthOfYear, dayOfMonth ->
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, monthOfYear)
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        updateDateInView()
    }
    private fun updateDateInView() {
        val myFormat = "dd-MMM-yyyy" // mention the format you need
        val sdf = SimpleDateFormat(myFormat, Locale.US)
        dateText.value = sdf.format(cal.time)
    }

    fun loadData()
    {
        viewModelScope.launch(Dispatchers.IO) {
            financeRepository.getInHandBalance()
            displayInWallet.postValue("₹${financeRepository.amountInWallet}")
            displayInDigitalWallet.postValue("₹${financeRepository.amountInDigitalWallet} in your digital wallet")
            pBarVisibility.value = false
        }
    }

    fun validate()
    {
        purpose = purpose.trim()
        payee = payee.trim()
        amount = amount.trim()

        if(amount.isEmpty() || amount == "")
        {
            errMsg = "This field is required"
            _stateFlow.value = 1
            return
        }
        if(dateText.value == "Select Date")
        {
            errMsg = "Please select a valid date"
            _stateFlow.value = 2
            _stateFlow.value = 0
            return
        }
        else {
            _stateFlow.value = 3
            _stateFlow.value = 0
        }
    }

    fun makeExpense(paymentMode: String)
    {
        pBarVisibility.value = true

        setUpLogger()
        viewModelScope.launch(Dispatchers.IO) {
            expenditureRepository.stateFlow.collectLatest {
                Logger.w(it)
                if(it == "") {
                    return@collectLatest
                }
                if(it.contains("Insufficient"))
                {
                    liveMsg.value = it
                }
                else
                {
                    val job = viewModelScope.async {
                        val task1 = async { logExpense(paymentMode) }
                        val task2 = async { updateExpenseCount() }
                        val task3 = async { logExpenditureStatistics() }
                        task1.await()
                        task2.await()
                        task3.await()
                        "Expense noted"
                    }

                    liveMsg.value = job.await()
                }
            }
        }

        if(paymentMode == "Bank" || paymentMode == "Debit Card")
        {
            viewModelScope.launch(Dispatchers.IO) {
                expenditureRepository.deductFromBank(amount)
                pBarVisibility.value = false
            }
        }
        if(paymentMode == "Cash")
        {
            viewModelScope.launch(Dispatchers.IO) {
                expenditureRepository.deductFromHand(amount)
                pBarVisibility.value = false
            }
        }
        if(paymentMode == "Digital Wallet")
        {
            viewModelScope.launch(Dispatchers.IO) {
                expenditureRepository.deductFromDigitalWallet(amount)
                pBarVisibility.value = false
            }
        }
        if(paymentMode == "Credit Card")
        {
            viewModelScope.launch(Dispatchers.IO) {
                expenditureRepository.addToCreditCardExpense(amount)
                pBarVisibility.value = false
            }
        }

    }

    private suspend fun logExpense(paymentMode: String)
    {
        expenditureRepository.logExpense(purpose, payee, paymentMode, dateText.value.toString(), amount)
    }

    private suspend fun updateExpenseCount()
    {
        expenditureRepository.updateExpenseCount(amount)
    }

    private suspend fun logExpenditureStatistics()
    {
        val dateToMonthAndYear = dateText.value.toString().split("-")
        val monthAndYear = dateToMonthAndYear[1].trim()+"-"+dateToMonthAndYear[2].trim()
        expenditureRepository.updateMonthlyStatistics(monthAndYear, amount)
    }

}