package repositories

import com.google.firebase.firestore.FirebaseFirestoreException
import com.orhanobut.logger.Logger
import kotlinx.coroutines.tasks.await
import utils.Common

class FinanceRepository(val authEmail: String) {

    var amountInWallet = 0.0
    var amountInDigitalWallet = 0.0
    var amountInBank = 0.0
    var amountUsingCreditCard = 0.0
    var listOfIncomes = arrayListOf<String>()

    val docRefIncomes = Common.collRef.document(authEmail).collection("FINANCE").document("INCOMES")
    val docRefData = Common.collRef.document(authEmail).collection("FINANCE").document("DATA")
    val docRefWithdraws = Common.collRef.document(authEmail).collection("FINANCE").document("WITHDRAWS")
    val docRefTransfers = Common.collRef.document(authEmail).collection("FINANCE").document("TRANSFERS")
    val docRefStatistics = Common.collRef.document(authEmail).collection("FINANCE").document("STATISTICS")
    val collRefMonthlyStatistics = Common.collRef.document(authEmail).collection("FINANCE").document("STATISTICS").collection("MONTHLY")

    suspend fun getInHandBalance()
    {
        val balInWallet = docRefData.get().await().get("in_wallet")
        amountInWallet = balInWallet!!.toString().toDouble()

        val balInDigitalWallet = docRefData.get().await().get("in_digital_wallet")
        amountInDigitalWallet = balInDigitalWallet!!.toString().toDouble()
    }

    suspend fun getInBankBalance()
    {
        val balInBank = docRefData.get().await().get("in_bank")
        amountInBank = balInBank!!.toString().toDouble()

        val creditCardExpenditure = docRefData.get().await().get("credit_card_expenditure")
        amountUsingCreditCard = creditCardExpenditure!!.toString().toDouble()

    }

    private suspend fun logIncome(date: String, amount: String, source: String, incomeMode: String)
    {
        val data = HashMap<String,Any>()
        val fieldName = authEmail.replace(".","_")+"-income-${Common.currentTime()}-$date".lowercase()
        val incomeArray = arrayListOf(fieldName, date, amount, source, incomeMode)
        data[fieldName] = incomeArray
        docRefIncomes.update(data)

        val dateToMonthAndYear = date.split("-")
        val monthAndYear = dateToMonthAndYear[1].trim()+"-"+dateToMonthAndYear[2].trim()
        updateIncomeCount(monthAndYear, amount)
    }

    suspend fun updateBankBalance(date: String, amount: String, source: String, creditCardExpenseUpdate: String)
    {
        if(!(amount.isEmpty() || amount == "")) {
            amountInBank += amount.toDouble()
            docRefData.update("in_bank",amountInBank.toString())
            logIncome(date, amount, source, "Received to bank")
        }
        if(!(creditCardExpenseUpdate.isEmpty() || creditCardExpenseUpdate=="")) {
            amountUsingCreditCard += creditCardExpenseUpdate.toDouble()
            docRefData.update("credit_card_expenditure", amountUsingCreditCard.toString())
        }
    }

    fun updateBalanceCashInHand(date: String, amount: String)
    {
        val data = HashMap<String,Any>()
        val fieldName = authEmail.replace(".","_")+"-withdraw-${Common.currentTime()}-$date".lowercase()
        val withdrawArray = arrayListOf(fieldName, date, amount)
        data[fieldName] = withdrawArray
        docRefWithdraws.update(data)

        amountInWallet += amount.toDouble()
        docRefData.update("in_wallet",amountInWallet.toString())
    }

    fun updateBalanceInDigitalWallet(date: String, amount: String)
    {
        val data = HashMap<String,Any>()
        val fieldName = authEmail.replace(".","_")+"-transfer-${Common.currentTime()}-$date".lowercase()
        val transferArray = arrayListOf(fieldName, date, amount)
        data[fieldName] = transferArray
        docRefTransfers.update(data)

        amountInDigitalWallet += amount.toDouble()
        docRefData.update("in_digital_wallet",amountInDigitalWallet.toString())
    }

    suspend fun updateOtherSourceIncome(date: String, amount: String, source: String, incomeMode: String)
    {
        if(incomeMode == "Received as cash")
        {
            amountInWallet += amount.toDouble()
            docRefData.update("in_wallet",amountInWallet.toString())
            logIncome(date, amount, source, incomeMode)
        }
        if(incomeMode == "Received to digital wallet")
        {
            amountInDigitalWallet += amount.toDouble()
            docRefData.update("in_digital_wallet",amountInDigitalWallet.toString())
            logIncome(date, amount, source, incomeMode)
        }
        if(incomeMode == "Received to bank")
        {
            amountInBank += amount.toDouble()
            docRefData.update("in_bank",amountInBank.toString())
            logIncome(date, amount, source, incomeMode)
        }
    }

    private suspend fun updateIncomeCount(date: String, newAmount: String)
    {
        var incomeCount = docRefStatistics.get().await().getLong("income_count")!!
        incomeCount += 1
        docRefStatistics.update("income_count",incomeCount)

        val amountValue = docRefStatistics.get().await().getLong("total_income_amount")!!
        val incomeAmount = amountValue +  newAmount.toLong()
        docRefStatistics.update("total_income_amount",incomeAmount)

        if(!collRefMonthlyStatistics.document(date).get().await().exists())
        {
            val data = HashMap<String,Any>()
            data["total_expenditure_amount"] = 0
            data["expenditure_count"] = 0
            data["total_income_amount"] = 0
            data["income_count"] = 0
            collRefMonthlyStatistics.document(date).set(data)
            updateMonthlyIncomeStatistics(date, newAmount)
        }
        else{
            updateMonthlyIncomeStatistics(date, newAmount)
        }
    }

    private suspend fun updateMonthlyIncomeStatistics(date: String, newAmount: String)
    {
        var monthlyIncomeCount = collRefMonthlyStatistics.document(date).get().await().getLong("income_count")!!
        monthlyIncomeCount += 1
        collRefMonthlyStatistics.document(date).update("income_count",monthlyIncomeCount)

        val monthlyAmountValue = collRefMonthlyStatistics.document(date).get().await().getLong("total_income_amount")!!
        val monthlyIncomeAmount = monthlyAmountValue +  newAmount.toLong()
        collRefMonthlyStatistics.document(date).update("total_income_amount",monthlyIncomeAmount)
    }

    suspend fun getIncomeData()
    {
        try {
            val result = docRefIncomes.get().await()
            for (expense in result.data!!.values)
                listOfIncomes.add(expense.toString())
        }
        catch (e: FirebaseFirestoreException)
        {
            Logger.e(e.message.toString())
        }
    }

}