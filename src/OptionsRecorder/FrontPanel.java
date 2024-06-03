package OptionsRecorder;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import com.ib.client.Bar;
import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDescription;
import com.ib.client.ContractDetails;
import com.ib.client.Decimal;
import com.ib.client.DeltaNeutralContract;
import com.ib.client.DepthMktDataDescription;
import com.ib.client.EClient;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.EWrapper;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.client.Execution;
import com.ib.client.ExecutionFilter;
import com.ib.client.FamilyCode;
import com.ib.client.HistogramEntry;
import com.ib.client.HistoricalSession;
import com.ib.client.HistoricalTick;
import com.ib.client.HistoricalTickBidAsk;
import com.ib.client.HistoricalTickLast;
import com.ib.client.NewsProvider;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.PriceIncrement;
import com.ib.client.SoftDollarTier;
import com.ib.client.TickAttrib;
import com.ib.client.TickAttribBidAsk;
import com.ib.client.TickAttribLast;

import OptionsRecorder.Security;
import OptionsRecorder.IBTextPanel;

public class FrontPanel extends JFrame implements EWrapper {
	
	private EJavaSignal m_signal = new EJavaSignal();
	private EClientSocket m_client = new EClientSocket(this, m_signal);
	private EReader m_reader;
	private IBTextPanel m_messages = new IBTextPanel("Messages", false);
	private Order m_order = new Order();
	public Contract m_contract = new Contract();
	ExecutionFilter new_filter;
	
	String account_number;
	
	String current_time;
	
	Security[] Security_data;
	
	// Just testing an idea on how to parse the option chain response
	String temp_ticker;
	ArrayList<Double> strikes_list = new ArrayList<Double>();
	int min_expiration = 99999999;
	int temp_conID;
	
	Dictionary<Integer, String> ticker_dict= new Hashtable<>();
	Dictionary<Integer, Double> strike_dict= new Hashtable<>();
	Dictionary<Integer, Integer> secDef_dict= new Hashtable<>();
	Dictionary<Integer, Integer> index_dict= new Hashtable<>();
	Dictionary<Integer, Integer> underlying_option_dict = new Hashtable<>();
	Dictionary<Integer, String> put_call_dict = new Hashtable<>();
	
	int window = 20;	
	
	int num_securities = 0;
	
	JPanel buttonPanel, messagePanel, mainPanel;
	int port_number;
	
	private boolean m_disconnectInProgress = false;
	
	int nextID;
	
	String todays_date = formatDate();
	
	FrontPanel() throws FileNotFoundException {
		
		get_account_info();
		create_futures();
		create_panel();
		
	}
	
	private String formatDate() {
		// gets today's date and finds if tomorrow is next expiration and converts to string

		Calendar currentDate = Calendar.getInstance(Locale.ENGLISH); //Get the current date
		int hourOfDay = currentDate.get(Calendar.HOUR_OF_DAY);
		int minOfHour = currentDate.get(Calendar.MINUTE);
		
		if (hourOfDay >= 13) {
			currentDate.add(Calendar.DATE, 1);
		}
		String year = String.valueOf(currentDate.get(Calendar.YEAR));
		String month = "";
		String day = "";
		int monthOfYear = currentDate.get(Calendar.MONTH);
		int dayOfMonth = currentDate.get(Calendar.DAY_OF_MONTH);
		
		monthOfYear++;
		
		if (monthOfYear < 10) {
			month = "0" + String.valueOf(monthOfYear);			
		}
		else {
			month = String.valueOf(monthOfYear);
		}
		if (dayOfMonth < 10) {
			day = "0" + String.valueOf(dayOfMonth);			
		}
		else {
			day = String.valueOf(dayOfMonth);
		}
		
		String te = year + month + day;
		return te;
	}

	protected void get_account_info() throws FileNotFoundException {
		// Get the account number from the local text file
		
		Scanner input = new Scanner(new File("Account.txt"));
		
		account_number = input.next();
		input.close();
		
	}
	
	protected void create_futures() throws FileNotFoundException{
		// Imports the data from "AccountData.txt" and creates an array
		// of Security Objects
		
		// It first creates lists of each variable so it can count them up
		// and create a variable number of securities
		ArrayList<String> ticker = new ArrayList<String>();
		ArrayList<String> exchange = new ArrayList<String>();
		ArrayList<String> security_type = new ArrayList<String>();
		ArrayList<Integer> multiplier = new ArrayList<Integer>();
		
		Scanner input = new Scanner(new File("AccountData.txt"));
		
		while (input.hasNext()) {
			ticker.add(input.next());
			exchange.add(input.next());
			security_type.add(input.next());
			multiplier.add(input.nextInt());	
			num_securities++;
		}
		input.close();
		
		Security_data = new Security[num_securities];
		
		for (int i = 0; i < num_securities; i++) {
			Security_data[i] = new Security();
			Security_data[i].ticker = ticker.get(i);
			Security_data[i].exchange = exchange.get(i);
			Security_data[i].security_type = security_type.get(i);
			Security_data[i].multiplier = multiplier.get(i);
		}
	}
	
	public void create_panel() {
		// Create the front panel
		JFrame frame = new JFrame();
		JPanel mainPanel = new JPanel();
		String title = "Main_tab";
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setTitle("Options Recorder");
		frame.getContentPane().setLayout(null);
		frame.setSize(1800,900);
		
		createButtonPanel();
		createMessagePanel();
		
		buttonPanel.setBounds(0, 0, 120, 700);
		//mainPanel.setBounds(130, 0, 1750, 900);
		
		messagePanel.setBounds(260, 10, 500, 300);
		//mainPanel.add(messagePanel);
		frame.add(buttonPanel);
		frame.add(messagePanel);
		frame.setVisible(true);
		
	}
	
	public void createButtonPanel() {
		buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(0, 1));
		
		JButton ConnectButton = new JButton();
		ConnectButton.setText("Connect to TWS");
		buttonPanel.add(ConnectButton);
		ConnectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				port_number = 7496;
				onConnect();
			}
		});

		JButton GatewayButton = new JButton();
		GatewayButton.setText("Connect to Gateway");
		buttonPanel.add(GatewayButton);
		GatewayButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				port_number = 4002;
				//onGatewayButton();
			}
		});
		
		JButton SecDefButton = new JButton();
		SecDefButton.setText("Req Sec Def");
		buttonPanel.add(SecDefButton);
		SecDefButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onReqSecDefButton();
			}
		});
		
		JButton UnderlyingButton = new JButton();
		UnderlyingButton.setText("Underlying Price");
		buttonPanel.add(UnderlyingButton);
		UnderlyingButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onUnderlyingPrice();
			}
		});
		
		JButton OptionChainButton = new JButton();
		OptionChainButton.setText("Option Info");
		buttonPanel.add(OptionChainButton);
		OptionChainButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onOptionChainButton();
			}
		});
		
		JButton OptionPriceButton = new JButton();
		OptionPriceButton.setText("Req Option Prices");
		buttonPanel.add(OptionPriceButton);
		OptionPriceButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onOptionPriceButton();
			}
		});
		
		JButton DisconnectButton = new JButton();
		DisconnectButton.setText("Disconnect");
		buttonPanel.add(DisconnectButton);
		DisconnectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onDisconnect();
			}
		});
		
		JButton HistoryButton = new JButton();
		HistoryButton.setText("History");
		buttonPanel.add(HistoryButton);
		HistoryButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onHistory();
			}
		});

	}
	
	private void createMessagePanel() {
		messagePanel = new JPanel();
		messagePanel.setLayout(new GridLayout(0, 1));
		messagePanel.add(m_messages);
	}
	
	void onHistory() {
		// This is just checking to see if requesting the options hisotyry is a better method
		Contract contract = new Contract();
		contract.symbol("NQ");
		contract.secType("FOP");
		contract.currency("USD");
		contract.exchange("CME");
		contract.conid(687507953);
		contract.multiplier("20");
		contract.strike(18500);
		
		m_client.reqHistoricalData(nextID, contract, "", "1 D", "1 min", "TRADES", 1, 1, false, null);	
		nextID++;
		
		System.out.println(" I tried to get historical data");
		
		
	}
	
	void onConnect() {

		m_disconnectInProgress = false;

		m_client.eConnect("", port_number, 0);
		if (m_client.isConnected()) {
			m_messages.add("Connected to Tws server version " + m_client.serverVersion() + " at "
					+ m_client.getTwsConnectionTime());
		}
		else {
			m_messages.add("Connection Failed");
		}

		m_client.reqCurrentTime();

		m_reader = new EReader(m_client, m_signal);

		m_reader.start();
		
		new Thread() {
			public void run() {
				processMessages();

				int i = 0;
				System.out.println(i);
			}
		}.start();

}
	
	private void processMessages() {

		while (m_client.isConnected()) {
			m_signal.waitForSignal();
			try {
				m_reader.processMsgs();
			} catch (Exception e) {
				error(e);
			}
		}
	}
	
	void onDisconnect() {
		// disconnect from TWS
		
		m_client.reqCurrentTime();
		m_messages.add("Disconnected at " + current_time);
		m_disconnectInProgress = true;
		m_client.eDisconnect();
	}

	void onReqSecDefButton() {
		
		// Request the info of the underlying contract to get the condID and expiration 
		
		long t1, t2;
		Contract contract = new Contract();
		
		for (int i=0;i<num_securities;i++) {
			min_expiration = 99999999;
			
			contract.symbol(Security_data[i].ticker);
			//contract.symbol("ES");
			contract.secType(Security_data[i].security_type);
			//contract.secType("FUT");
			contract.currency("USD");
			contract.exchange(Security_data[i].exchange);
			//contract.exchange("CME");

			// System.out.println("Line 247");
			m_client.reqContractDetails(nextID,contract);
			secDef_dict.put(nextID, i);
			
			nextID++;
			t1 = System.currentTimeMillis();
			do {
				t2 = System.currentTimeMillis();
			} while (t2 - t1 < 1000);
		}
		debugPrint(297);
		m_messages.add("Complete requesting Underlying");
		for (int i=0;i<num_securities;i++) {
			
		}
	}
	
	void onUnderlyingPrice() {
		// This gets the underlying price of each asset so it knows what options to request
		
		long t1, t2;
		Contract contract = new Contract();
		
		for (int i=0;i<num_securities;i++) {
			contract.symbol(Security_data[i].ticker);
			contract.secType("FUT");
			contract.currency("USD");
			contract.exchange(Security_data[i].exchange);
			contract.lastTradeDateOrContractMonth(String.valueOf(Security_data[i].expiration));
			contract.conid(Security_data[i].conID);
			contract.multiplier(String.valueOf(Security_data[i].multiplier));
			index_dict.put(nextID, i);
			underlying_option_dict.put(nextID, 1);
			m_client.reqMktData(nextID,contract,"",false,false,null);
			
			t1 = System.currentTimeMillis();
			do {
				t2 = System.currentTimeMillis();
			} while (t2 - t1 < 1000);
			m_client.cancelMktData(nextID);
			nextID++;
		}

		
		m_messages.add("Complete requesting Underlying Price");
				
	}
	
	void onOptionChainButton() {
		/* When the Option Chain button is clicked, this gets a list of today's strikes*/
		
		int index = 0;
		long t1, t2;
		for (int i=0; i<num_securities; i++) {
			min_expiration = 99999999;
			m_client.reqSecDefOptParams(nextID,
					Security_data[i].ticker,
					Security_data[i].exchange,
					Security_data[i].security_type,
					Security_data[i].conID);
			// m_client.reqSecDefOptParams(nextID,"CL","NYMEX","FUT",212921504);
			//m_client.reqSecDefOptParams(nextID,"ES","CME","FOP",551601561);
			//m_client.reqSecDefOptParams(nextID,"NQ","CME","FUT",620730920);
			nextID++;
			t1 = System.currentTimeMillis();
			do {
				t2 = System.currentTimeMillis();
			} while (t2 - t1 < 1000);
			Collections.sort(strikes_list);
			index = findIndexofConID(temp_conID);
			int len = strikes_list.size();
			Security_data[index].strikes = new double[len];
			for (int j = 0; j < len; j++){
				Security_data[index].strikes[j] = strikes_list.get(j);
			}
		}
		
		for (int i=0;i<num_securities;i++) {
			Security_data[i].requested_strikes = new double[window];
			Security_data[i].process_strikes(window);
		}
		
		m_messages.add("Complete requesting Option Chains");
		
	}
	
	void onOptionPriceButton() {
		// Hopefully this gets the entire option chain streaming through
		
		for (int i = 0; i < num_securities; i++) {
			for (int j = 0; j < window; j++) {
				Contract contract = new Contract();
				contract.symbol(Security_data[i].ticker);
				contract.secType("FOP");
				contract.currency("USD");
				contract.exchange("CME");
				contract.lastTradeDateOrContractMonth(todays_date);
				contract.strike(Security_data[i].requested_strikes[j]);
				contract.multiplier(String.valueOf(Security_data[i].multiplier));
				
				// First Request the PUT
				contract.right("PUT");
				put_call_dict.put(nextID, "PUT");
				index_dict.put(nextID, i);
				underlying_option_dict.put(nextID, 0);
				strike_dict.put(nextID, Security_data[i].requested_strikes[j]);
				m_client.reqMktData(nextID,contract,"",false,false,null);
				nextID++;
				
				// Then Request the Call
				contract.right("CALL");
				put_call_dict.put(nextID, "CALL");
				index_dict.put(nextID, i);
				underlying_option_dict.put(nextID, 0);
				strike_dict.put(nextID, Security_data[i].requested_strikes[j]);
				m_client.reqMktData(nextID,contract,"",false,false,null);
				nextID++;
			}


		}
	}
	

	@Override
	public void tickPrice(int tickerId, int field, double price, TickAttrib attrib) {
		// TODO Auto-generated method stub

		if (field == 1) {
			if (underlying_option_dict.get(tickerId) == 1) {
				Security_data[index_dict.get(tickerId)].current_price = price;
				System.out.println(Security_data[index_dict.get(tickerId)].ticker +
						" " + " Underlying Price = " + price);
			}
			else {
				System.out.println(Security_data[index_dict.get(tickerId)].ticker +
						"" + put_call_dict.get(tickerId) + 
						" " + " Strike = " + " " + strike_dict.get(tickerId) + " Bid = " + price);
			}
		}
		else if (field == 2) {
			/*
			if (underlying_option_dict.get(tickerId) != 1) {
				System.out.println(Security_data[index_dict.get(tickerId)].ticker +
					" " + " Strike = " + " " + strike_dict.get(tickerId) + " Ask = " + price);
			}
			*/
			
		}
	}

	@Override
	public void tickSize(int tickerId, int field, Decimal size) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickOptionComputation(int tickerId, int field, int tickAttrib, double impliedVol, double delta,
			double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
		// TODO Auto-generated method stub
		if (field == 13) {
			System.out.println(Security_data[index_dict.get(tickerId)].ticker + " Option Price = " + optPrice + " " + " Delta " + " " + delta + 
					" underlying price = " + undPrice);
		}
		
		
	}

	@Override
	public void tickGeneric(int tickerId, int tickType, double value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickString(int tickerId, int tickType, String value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,
			double impliedFuture, int holdDays, String futureLastTradeDate, double dividendImpact,
			double dividendsToLastTradeDate) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void orderStatus(int orderId, String status, Decimal filled, Decimal remaining, double avgFillPrice,
			int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void openOrderEnd() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountValue(String key, String value, String currency, String accountName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updatePortfolio(Contract contract, Decimal position, double marketPrice, double marketValue,
			double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountTime(String timeStamp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void accountDownloadEnd(String accountName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void nextValidId(int orderId) {
		// TODO Auto-generated method stub
		String msg, temp1, temp2;
		temp1 = "The next Valid Order ID is: ";
		temp2 = Integer.toString(orderId);
		msg = temp1 + temp2;
		m_messages.add(msg);
		nextID = orderId;
		System.out.println("Line 366");
		
	}

	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub
		System.out.println(contractDetails.realExpirationDate());
		//int index = findIndexofTicker(contractDetails.marketName());
		int index = secDef_dict.get(reqId);
		
		if (Integer.valueOf(contractDetails.realExpirationDate()) < min_expiration) {
			min_expiration = Integer.valueOf(contractDetails.realExpirationDate());
			Security_data[index].conID = contractDetails.conid();
			Security_data[index].expiration = min_expiration;			
		}
	}

	@Override
	public void bondContractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetailsEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetails(int reqId, Contract contract, Execution execution) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetailsEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateMktDepth(int tickerId, int position, int operation, int side, double price, Decimal size) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price,
			Decimal size, boolean isSmartDepth) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void managedAccounts(String accountsList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void receiveFA(int faDataType, String xml) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalData(int reqId, Bar bar) {
		// TODO Auto-generated method stub
		System.out.println("HistoricalData. " + reqId +
				" - Time: " + bar.time() +
				", Open: " + bar.open() +
				", High: " + bar.high() + 
                ", Low: " + bar.low() +
                ", Close: " + bar.close());
	}

	@Override
	public void scannerParameters(String xml) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark,
			String projection, String legsStr) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerDataEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close, Decimal volume,
			Decimal wap, int count) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void currentTime(long time) {
		// TODO Auto-generated method stub
		String date = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date (time*1000));
		System.out.println("Time line on 469" + date);
		current_time = date;
		
	}

	@Override
	public void fundamentalData(int reqId, String data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deltaNeutralValidation(int reqId, DeltaNeutralContract deltaNeutralContract) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickSnapshotEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void marketDataType(int reqId, int marketDataType) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void commissionReport(CommissionReport commissionReport) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void position(String account, Contract contract, Decimal pos, double avgCost) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void positionEnd() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void accountSummary(int reqId, String account, String tag, String value, String currency) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void accountSummaryEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void verifyMessageAPI(String apiData) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void verifyCompleted(boolean isSuccessful, String errorText) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void verifyAndAuthMessageAPI(String apiData, String xyzChallenge) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayGroupList(int reqId, String groups) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayGroupUpdated(int reqId, String contractInfo) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void error(Exception e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void error(String str) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void error(int id, int errorCode, String errorMsg, String advancedOrderRejectJson) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connectionClosed() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connectAck() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void positionMulti(int reqId, String account, String modelCode, Contract contract, Decimal pos,
			double avgCost) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void positionMultiEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void accountUpdateMulti(int reqId, String account, String modelCode, String key, String value,
			String currency) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void accountUpdateMultiEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId,
			String tradingClass, String multiplier, Set<String> expirations, Set<Double> strikes) {
		
		// TODO Auto-generated method stub
		temp_conID = underlyingConId;
		ArrayList<String> list = new ArrayList<String>(expirations);
		int temp_num = Integer.parseInt(list.get(0));
		ArrayList<Double> temp_list = new ArrayList<Double>(strikes);
		if (temp_num < min_expiration) {
			strikes_list.clear();
			min_expiration = temp_num;
			strikes_list = temp_list;
		}
		//System.out.println("Line 681 " + underlyingConId + " " + min_expiration + " " + strikes_list);
		//System.out.println("Security Definition Optional Parameter: " + EWrapperMsgGenerator.securityDefinitionOptionalParameter(reqId, exchange, underlyingConId, tradingClass, multiplier, expirations, strikes));
	}

	@Override
	public void securityDefinitionOptionalParameterEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void softDollarTiers(int reqId, SoftDollarTier[] tiers) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void familyCodes(FamilyCode[] familyCodes) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {
		// TODO Auto-generated method stub
		System.out.println("Historical Data End");
		
	}

	@Override
	public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline,
			String extraData) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void smartComponents(int reqId, Map<Integer, Entry<String, Character>> theMap) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void newsProviders(NewsProvider[] newsProviders) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void newsArticle(int requestId, int articleType, String articleText) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalNewsEnd(int requestId, boolean hasMore) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void headTimestamp(int reqId, String headTimestamp) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void histogramData(int reqId, List<HistogramEntry> items) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalDataUpdate(int reqId, Bar bar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void rerouteMktDataReq(int reqId, int conId, String exchange) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void rerouteMktDepthReq(int reqId, int conId, String exchange) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void pnlSingle(int reqId, Decimal pos, double dailyPnL, double unrealizedPnL, double realizedPnL,
			double value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickByTickAllLast(int reqId, int tickType, long time, double price, Decimal size,
			TickAttribLast tickAttribLast, String exchange, String specialConditions) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, Decimal bidSize,
			Decimal askSize, TickAttribBidAsk tickAttribBidAsk) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickByTickMidPoint(int reqId, long time, double midPoint) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void orderBound(long orderId, int apiClientId, int apiOrderId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void completedOrder(Contract contract, Order order, OrderState orderState) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void completedOrdersEnd() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void replaceFAEnd(int reqId, String text) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void wshMetaData(int reqId, String dataJson) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void wshEventData(int reqId, String dataJson) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalSchedule(int reqId, String startDateTime, String endDateTime, String timeZone,
			List<HistoricalSession> sessions) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void userInfo(int reqId, String whiteBrandingId) {
		// TODO Auto-generated method stub
		
	}
	
	public int findIndexofTicker(String ticker) {
		int index = -1;
		for (int i = 0 ; i < num_securities; i++) {
			if (ticker.equals(Security_data[i].ticker)) {
				index = i;
			}
		}
		
		return index;
	}
	
	public int findIndexofConID(int conID) {
		int index = -1;
		for (int i = 0 ; i < num_securities; i++) {
			if (conID == Security_data[i].conID) {
				index = i;
			}
		}
		
		if (index == -1) {
			System.out.println("Something went wrong on line 1112" + conID);
		}
		
		return index;
	}
	
	public void debugPrint(int line) {
		System.out.println("Debugging on line " + line);
		for (int i = 0 ; i < num_securities; i++) {
			System.out.println(Security_data[i].ticker + " " + Security_data[i].conID + " " + Security_data[i].expiration);					
		}
	}

}
