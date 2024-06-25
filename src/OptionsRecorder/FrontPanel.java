package OptionsRecorder;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
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
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

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
import com.ib.client.Util;

import OptionsRecorder.Security;
import OptionsRecorder.IBTextPanel;

public class FrontPanel extends JFrame implements EWrapper {
	
	private EJavaSignal m_signal = new EJavaSignal();
	private EClientSocket m_client = new EClientSocket(this, m_signal);
	private EReader m_reader;
	private IBTextPanel m_messages = new IBTextPanel("Messages", false);
	private Order m_order = new Order();
	public Contract m_contract = new Contract();
	ExecutionFilter new_filter = new ExecutionFilter();
	Timer timer = new Timer();
	String account_number;
	
	String current_time;
	
	Security[] Security_data;
	
	int acctPaneNumRows = 2, acctPaneNumColumns = 7;
	
	// Just testing an idea on how to parse the option chain response
	String temp_ticker;
	ArrayList<Double> strikes_list = new ArrayList<Double>();
	int min_expiration = 99999999;
	int temp_conID;
	
	Dictionary<Integer, String> ticker_dict = new Hashtable<>();
	Dictionary<Integer, Double> strike_dict = new Hashtable<>();
	Dictionary<Integer, Integer> conID_dict = new Hashtable<>();
	Dictionary<Integer, Integer> strike_to_window_dict = new Hashtable<>();
	Dictionary<Integer, Integer> secDef_dict= new Hashtable<>();
	Dictionary<Integer, Integer> index_dict= new Hashtable<>();
	Dictionary<Integer, Integer> underlying_option_dict = new Hashtable<>();
	Dictionary<Integer, String> put_call_dict = new Hashtable<>();
	
	int window = 100;
	int accountKeyCounter = 0;
	int orderCounter = 0;
	int transCounter = 0;
	int portfolioRowNumber = 0;
	int portPaneNumColumns = 10;
	int portPaneNumRows = 15;
    int orderPaneNumColumns = 7;
    int orderPaneNumRows = 20;
    int transPaneNumRows = 20;
    int transPaneNumColumns = 8;
	
	int num_securities = 0;
	
	JFrame mainFrame;
	JTabbedPane tabbedPane;
	JPanel buttonPanel, messagePanel, mainPanel, tabbedPanel[];
	JTable acctPane, portPane, orderPane, transPane, optionsTable[], underlyingTable[];
	int port_number;
	JScrollPane optionsScroll[], portPaneScroll, transPaneScroll, orderPaneScroll, underlyingScroll[];
	
	private boolean m_disconnectInProgress = false;
	
	int nextID;
	
	String todays_date = formatDate();
	int todays_date_int = Integer.valueOf(todays_date);
	
	// These are declared here to save time initializing them here instead of in the return option computation
	int tickTickerID = 0;
	String tick_put_call = "CALL";
	int tick_strike_to_window = 0;
	int greekTickerID = 0;
	String greek_put_call = "CALL";
	int greek_strike_to_window = 0;
	
	FrontPanel() throws FileNotFoundException {
		
		get_account_info();
		create_futures();
		create_main_panel();
		create_tabbed_panel();
		create_frame();
		
	    timer.scheduleAtFixedRate(new RemindTask(), 2*1000, //initial delay
		  	      5 * 60 * 1000);
		
	}
	
	class RemindTask extends TimerTask{
		//This function is to print the Options data, disconnect from IB and close the window
		
		public void run(){
			Calendar currentDate = Calendar.getInstance(Locale.ENGLISH); //Get the current date
			int hourOfDay = currentDate.get(Calendar.HOUR_OF_DAY);
			int minOfHour = currentDate.get(Calendar.MINUTE);
			
			if (hourOfDay == 13 ){
				if (minOfHour >= 01 && minOfHour <= 10){
					onDisconnect();
					try {
						onPrint();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					System.exit(0);
				}				
			}
		}
	}
	
	private String formatDate() {
		// gets today's date and finds if tomorrow is next expiration and converts to string

		Calendar currentDate = Calendar.getInstance(Locale.ENGLISH); //Get the current date
		int hourOfDay = currentDate.get(Calendar.HOUR_OF_DAY);
		int minOfHour = currentDate.get(Calendar.MINUTE);
		
		if (hourOfDay >= 14) {
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
		ArrayList<String> stock = new ArrayList<String>();
		ArrayList<String> stock_exchange = new ArrayList<String>();
		
		Scanner input = new Scanner(new File("AccountData.txt"));
		input.nextLine();
		while (input.hasNext()) {
			ticker.add(input.next());
			exchange.add(input.next());
			security_type.add(input.next());
			multiplier.add(input.nextInt());
			stock.add(input.next());
			stock_exchange.add(input.next());			
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
			Security_data[i].stock_ticker = stock.get(i);
			Security_data[i].stock_exchange = stock_exchange.get(i);
		}
	}
	
	public void create_tabbed_panel() {
		
		// Creates the ticker specific tabs and adds them to the tabbed pane
		
		tabbedPane = new JTabbedPane();
		tabbedPane.setBounds(0,0,1800,900);
		tabbedPanel = new JPanel[num_securities];
		optionsTable = new JTable[num_securities];
		optionsScroll = new JScrollPane[num_securities];
		underlyingTable = new JTable[num_securities];
		underlyingScroll = new JScrollPane[num_securities]; 
		String temp_ticker = "";

		String titles[] = {"Bid","Call","Delta","Strike","Bid","Call","Delta"};
		String undertitles[] = {"Ticker","Current Price"};
		
		tabbedPane.addTab("Main",mainPanel);
		
		// Creates a JPanel for each ticker
		for (int i = 0; i < num_securities; i++) {
			temp_ticker = Security_data[i].ticker;
			tabbedPanel[i] = new JPanel();
			DefaultTableModel table_model=new DefaultTableModel(titles,window);
			optionsTable[i] = new JTable(table_model);
			optionsScroll[i] = new JScrollPane(optionsTable[i]);
			optionsScroll[i].setColumnHeaderView(optionsTable[i].getTableHeader());
			optionsScroll[i].setBounds(10,150,1770,650);
			
			DefaultTableModel table_model1 = new DefaultTableModel(undertitles,1);
			underlyingTable[i] = new JTable(table_model1);
			underlyingScroll[i] = new JScrollPane(underlyingTable[i]);
			underlyingScroll[i].setColumnHeaderView(underlyingTable[i].getTableHeader());
			underlyingScroll[i].setBounds(10,10,1770,130);
			
			tabbedPanel[i].setLayout(null);
			tabbedPanel[i].setBounds(0,0,1800,900);
			tabbedPanel[i].add(optionsScroll[i]);
			tabbedPanel[i].add(underlyingScroll[i]);
			tabbedPane.addTab(temp_ticker, tabbedPanel[i]);
			
			underlyingTable[i].setValueAt(Security_data[i].ticker, 0, 0);
		}
	}
	
	public void create_main_panel() {
		// Create the front panel

		mainPanel = new JPanel();
		String title = "Main_tab";
		mainPanel.setLayout(null);
		mainPanel.setBounds(0,0,1800,900);
		
		createButtonPanel();
		createAccountPanel();
		createPortfolioPanel();
		createOrderPanel();
		createTransPanel();
		createMessagePanel();
		
		buttonPanel.setBounds(0, 0, 120, 700);
		acctPane.setBounds(130,10,250,200);
		portPaneScroll.setBounds(130,325,700,400);
		orderPaneScroll.setBounds(850,325,500,400);
		transPaneScroll.setBounds(400,10,500,300);
		
		
		messagePanel.setBounds(925, 10, 500, 300);
		//mainPanel.add(messagePanel);
		mainPanel.add(buttonPanel);
		mainPanel.add(acctPane);
		mainPanel.add(portPaneScroll);
		mainPanel.add(orderPaneScroll);
		mainPanel.add(transPaneScroll);
		mainPanel.add(messagePanel);		
	}
	
	public void create_frame() {
		mainFrame = new JFrame();
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setTitle("Options Recorder");
		mainFrame.getContentPane().setLayout(null);
		mainFrame.setSize(1800,900);
		
		mainFrame.add(tabbedPane);
		mainFrame.setVisible(true);
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
		
		JButton RefreshButton = new JButton();
		RefreshButton.setText("Refresh");
		buttonPanel.add(RefreshButton);
		RefreshButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onRefreshButton();
			}
		});
		
		JButton OrdersButton = new JButton();
		OrdersButton.setText("Orders");
		buttonPanel.add(OrdersButton);
		OrdersButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onGetOrdersButton();
			}
		});
		
		JButton TransButton = new JButton();
		TransButton.setText("Transactions");
		buttonPanel.add(TransButton);
		TransButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onTransButton();
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
		
		JButton PrintDataButton = new JButton();
		PrintDataButton.setText("PrintDataButton");
		buttonPanel.add(PrintDataButton);
		PrintDataButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					onPrint();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		});

	}
	
	private void createAccountPanel(){
		// Creates the account summary table
		acctPane = new JTable(acctPaneNumColumns, 2);
		for (int i = 0; i<2;i++){
			acctPane.getColumnModel().getColumn(i).setPreferredWidth(5);
		}
}
	
private void createPortfolioPanel(){
	// Creates the portfolio panel.
		
	String titles[] = {"Ticker",
			"Call/Put",
			"Strike",
			"Expiration",
			"Postion",
			"Market Price",
			"Market Value",
			"Average Cost",
			"Unrealized P/L",
			"Realized P/L"};
	
		// portPane = new JTable(portPaneNumRows, portPaneNumColumns);
	DefaultTableModel table_model = new DefaultTableModel(titles,portPaneNumRows);
	portPane = new JTable(table_model);
	portPaneScroll = new JScrollPane(portPane);
		/*for (int i = 0; i< portPaneNumColumns; i++){
			portPane.getColumnModel().getColumn(i).setPreferredWidth(120);
		}
		//portPane.setFont(new Font("Arial", Font.BOLD, 12));
		portPane.setValueAt("Ticker", 0, 0);
		portPane.setValueAt("Call/Put", 0, 1);
		portPane.setValueAt("Strike", 0, 2);
		portPane.setValueAt("Expiration", 0, 3);
		portPane.setValueAt("Postion", 0, 4);
		portPane.setValueAt("Market Price", 0, 5);
		portPane.setValueAt("Market Value", 0, 6);
		portPane.setValueAt("Average Cost", 0, 7);
		portPane.setValueAt("Unrealized P/L", 0, 8);
		portPane.setValueAt("Realized P/L", 0, 9);*/
	}

private void createOrderPanel(){
	
	String titles[] = {"Contract",
			"Expiration",
			"Qnty",
			"Order Type",
			"Price",
			"Limit Price",
			"Aux Price"};
	
	DefaultTableModel table_model=new DefaultTableModel(titles,orderPaneNumRows);
	orderPane = new JTable(table_model);
	orderPaneScroll = new JScrollPane(orderPane);
	/*
	orderPane.setValueAt("Contract", 0, 0);
	orderPane.setValueAt("Expiration", 0, 1);
	orderPane.setValueAt("Qnty", 0, 2);
	orderPane.setValueAt("Order Type", 0, 3);
	orderPane.setValueAt("Price", 0, 4);
	orderPane.setValueAt("Limit Price", 0, 5);
	orderPane.setValueAt("Aux Price", 0, 6);*/	
}

private void createTransPanel(){
	
	String titles[] = {"Contract",
			"Sold/Bought",
			"Call/Put",
			"Strike",
			"Expiration",
			"Qnty",
			"Price",
			"Time/Date"};
	
	DefaultTableModel table_model = new DefaultTableModel(titles,transPaneNumRows);
	transPane = new JTable(table_model);
	transPaneScroll = new JScrollPane(transPane);
	
	// transPane = new JTable(transPaneNumRows, transPaneNumColumns);
	/*transPane.setValueAt("Contract", 0, 0);
	transPane.setValueAt("Sold/Bought", 0, 1);
	transPane.setValueAt("Call/Put", 0, 2);
	transPane.setValueAt("Strike", 0, 3);
	transPane.setValueAt("Expiration", 0, 4);
	transPane.setValueAt("Qnty", 0, 5);
	transPane.setValueAt("Price", 0, 6);	
	transPane.setValueAt("Time/Date", 0, 7);*/
}
	
	private void onPrint() throws IOException {
		// WRites the data to separate output files that have the data
		
		String file_name = "";
		
		for (int i = 0; i < num_securities; i++) {

			file_name = Security_data[i].ticker + formatDate()+".txt"; 
			File file = new File(file_name);
			FileWriter fw;
			fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			for (int j = 0; j < 2; j++) {
				for (int k = 0; k < window; k++) {
					for (int l = 0; l <= 6.5 * 60; l++) {
						if (j == 0) {
							bw.write(
									Security_data[i].ticker + " " +
							"PUT " + (int)Security_data[i].requested_strikes[k] + " Time index = " + l + " data = " +
							Security_data[i].data[j][k][l][0] + " " + Security_data[i].data[j][k][l][1] + " " +
							Security_data[i].data[j][k][l][2] + " " + Security_data[i].data[j][k][l][3] + " " +
							Security_data[i].data[j][k][l][4] + " " + Security_data[i].data[j][k][l][5] + " " +
							Security_data[i].data[j][k][l][6] + " " + Security_data[i].data[j][k][l][7] + " " +
							Security_data[i].data[j][k][l][8] + " " + Security_data[i].data[j][k][l][9]);
							bw.newLine();
						}
						else {
							bw.write(
									Security_data[i].ticker + " " +
							"CALL " + (int)Security_data[i].requested_strikes[k] + " Time index = " + l + " data = " +
							Security_data[i].data[j][k][l][0] + " " + Security_data[i].data[j][k][l][1] + " " +
							Security_data[i].data[j][k][l][2] + " " + Security_data[i].data[j][k][l][3] + " " +
							Security_data[i].data[j][k][l][4] + " " + Security_data[i].data[j][k][l][5] + " " +
							Security_data[i].data[j][k][l][6] + " " + Security_data[i].data[j][k][l][7] + " " +
							Security_data[i].data[j][k][l][8] + " " + Security_data[i].data[j][k][l][9]);
							bw.newLine();							
						}
					}
				}
			}
			bw.close();
		}
	}
	
	private void createMessagePanel() {
		messagePanel = new JPanel();
		messagePanel.setLayout(new GridLayout(0, 1));
		messagePanel.add(m_messages);
	}
	
	void onHistory() {
		// This is just checking to see if requesting the options history is a better method
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
	
	protected void onRefreshButton() {
		// Gets the account information

				
		// Reset the rows in the account pane and the portfolio pane
		portfolioRowNumber = 0;
		accountKeyCounter = 0;
		
		/*
		for (int ii = 1; ii<portPaneNumRows;ii++){
			for (int jj = 0; jj<portPaneNumColumns; jj++){
				portPane.setValueAt("", ii, jj);
			}
		}*/
		
		m_client.reqAccountUpdates( true, account_number);
		//System.out.println("The portfolio was updated " + portfolioCounter + " times");
		
		long t0,t1;
		t0=System.currentTimeMillis();
        do{
            t1=System.currentTimeMillis();
        }
        while (t1-t0<1000);
				
		m_client.reqAccountUpdates( false, account_number);		
	}
	
	protected void onGetOrdersButton(){
		orderCounter = 0;
		/*
		orderPane.setValueAt("Contract", 0, 0);
		orderPane.setValueAt("Expiration", 0, 1);
		orderPane.setValueAt("Qnty", 0, 2);
		orderPane.setValueAt("Order Type", 0, 3);
		orderPane.setValueAt("Price", 0, 4);
		orderPane.setValueAt("Limit Price", 0, 5);
		orderPane.setValueAt("Aux Price", 0, 6);*/
		for (int ii = 0; ii<orderPaneNumRows;ii++){
			for (int jj = 0; jj<orderPaneNumColumns; jj++){
				orderPane.setValueAt("", ii, jj);
			}
		}
		m_client.reqAllOpenOrders();
	}
	
	protected void onTransButton(){
		transCounter = 0;
		for (int ii = 0; ii<transPaneNumRows;ii++){
			for (int jj = 0; jj<transPaneNumColumns; jj++){
				transPane.setValueAt("", ii, jj);
			}
		}
		m_client.reqExecutions(1, new_filter);
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
			conID_dict.put(nextID, i);
			// m_client.reqSecDefOptParams(nextID,"CL","NYMEX","FUT",212921504);
			//m_client.reqSecDefOptParams(nextID,"ES","CME","FOP",551601561);
			//m_client.reqSecDefOptParams(nextID,"NQ","CME","FUT",620730920);
			
			t1 = System.currentTimeMillis();
			do {
				t2 = System.currentTimeMillis();
			} while (t2 - t1 < 2000);
			Collections.sort(strikes_list);
			
			//index = findIndexofConID(temp_conID);
			index = conID_dict.get(nextID);
			int len = strikes_list.size();
			Security_data[index].strikes = new double[len];
			for (int j = 0; j < len; j++){
				Security_data[index].strikes[j] = strikes_list.get(j);
			}
			nextID++;
			debugPrint2(831,i,min_expiration);
		}
		
		for (int i=0; i < num_securities; i++) {
			Security_data[i].requested_strikes = new double[window];
			Security_data[i].process_strikes(window);
			//Security_data[i].data = new double [2][window][390][6];
			Security_data[i].data = new double [2][window][390+1][10];
			Security_data[i].initialize_data();
			for (int j = 0; j < window; j++) {
				optionsTable[i].setValueAt(Security_data[i].requested_strikes[j], j, 3);
			}
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
				contract.tradingClass(Security_data[i].tradeclass);

				
				// First Request the PUT
				contract.right("PUT");
				put_call_dict.put(nextID, "PUT");
				index_dict.put(nextID, i);
				underlying_option_dict.put(nextID, 0);
				strike_dict.put(nextID, Security_data[i].requested_strikes[j]);
				strike_to_window_dict.put(nextID, j);
				m_client.reqMktData(nextID,contract,"",false,false,null);
				nextID++;
				
				// Then Request the Call
				contract.right("CALL");
				put_call_dict.put(nextID, "CALL");
				index_dict.put(nextID, i);
				underlying_option_dict.put(nextID, 0);
				strike_dict.put(nextID, Security_data[i].requested_strikes[j]);
				strike_to_window_dict.put(nextID, j);
				m_client.reqMktData(nextID,contract,"",false,false,null);
				nextID++;
			}


		}
	}
	

	@Override
	public void tickPrice(int tickerId, int field, double price, TickAttrib attrib) {
		// TODO Auto-generated method stub
		
		tickTickerID = index_dict.get(tickerId);

		if (field == 1) {
			if (underlying_option_dict.get(tickerId) == 1) {
				Security_data[tickTickerID].current_price = price;
				System.out.println(Security_data[tickTickerID].ticker +
						" " + " Underlying Price = " + price);
			}
			else {
				tick_put_call = put_call_dict.get(tickerId);
				tick_strike_to_window = strike_to_window_dict.get(tickerId);
				/*System.out.println(Security_data[tickTickerID].ticker +
						"" + put_call_dict.get(tickerId) + 
						" " + " Strike = " + " " + strike_dict.get(tickerId) + " Bid = " + price + " " + tick_put_call);*/
				
				Security_data[tickTickerID].process_price_data(
						tick_put_call,
						tick_strike_to_window,
						price, 0);
				
				if (tick_put_call == "CALL") {
					optionsTable[index_dict.get(tickerId)].setValueAt(price, tick_strike_to_window, 0);
				}
				else {
					optionsTable[index_dict.get(tickerId)].setValueAt(price, tick_strike_to_window, 4);
				}
			}
		}
		else if (field == 2) {
			tick_put_call = put_call_dict.get(tickerId);
			tick_strike_to_window = strike_to_window_dict.get(tickerId);
			Security_data[tickTickerID].process_price_data(
					tick_put_call,
					tick_strike_to_window,
					price, 1);
			if (tick_put_call == "CALL") {
				optionsTable[index_dict.get(tickerId)].setValueAt(price, tick_strike_to_window, 1);
			}
			else {
				optionsTable[index_dict.get(tickerId)].setValueAt(price, tick_strike_to_window, 5);
			}
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

			/*System.out.println(Security_data[index_dict.get(tickerId)].ticker + " Option Price = " + optPrice + " " + " Delta " + " " + delta + 
					" underlying price = " + undPrice);*/

			greekTickerID = index_dict.get(tickerId);
			greek_put_call = put_call_dict.get(tickerId);
			greek_strike_to_window = strike_to_window_dict.get(tickerId);
			Security_data[greekTickerID].process_delta_data(greek_put_call,
					greek_strike_to_window,
					delta,
					undPrice);
			if (greek_put_call == "CALL") {
				optionsTable[greekTickerID].setValueAt(delta, greek_strike_to_window, 2);
			}
			else {
				optionsTable[greekTickerID].setValueAt(delta, greek_strike_to_window, 6);
			}
			underlyingTable[greekTickerID].setValueAt(undPrice, 0, 1);
			
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
		// Fills in the order pane
		
		orderPane.setValueAt(contract.symbol(), orderCounter, 0);
		orderPane.setValueAt(contract.lastTradeDateOrContractMonth(), orderCounter, 1);
		orderPane.setValueAt(order.totalQuantity(), orderCounter, 2);
		orderPane.setValueAt(order.action(), orderCounter, 3);
		orderPane.setValueAt(order.orderType(), orderCounter, 4);
		orderPane.setValueAt(order.lmtPrice(), orderCounter, 5);
		orderPane.setValueAt(order.auxPrice(), orderCounter, 6);
		orderCounter = orderCounter + 1;
		
	}

	@Override
	public void openOrderEnd() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountValue(String key, String value, String currency, String accountName) {
		// TODO Auto-generated method stub
		//System.out.println(key + " " + value + " " + accountName + " " + keyCounter);
				//if (keyCounter == 32){
				if (key.equals("ExcessLiquidity")){
					//acctPaneTitles.add(key);
					//acctPaneValues[acctPaneValuesCounter] = Double.parseDouble(value);
					//acctPaneValuesCounter = acctPaneValuesCounter + 1;
					acctPane.setValueAt(key, 0, 0);
					acctPane.setValueAt(value, 0, 1);
					//accountCounter = accountCounter + 1;
					//System.out.println(key + " " + value + " " + keyCounter + " " + sdf.format(cal.getTime()));
				}
				if (key.equals("InitMarginReq")){
				//if (keyCounter == 62){
					//acctPaneTitles.add(key);
					//acctPaneValues[acctPaneValuesCounter] = Double.parseDouble(value);
					//acctPaneValuesCounter = acctPaneValuesCounter + 1;
					//acctPane.setValueAt(acctPaneTitles.get(1), 1, 0);
					//acctPane.setValueAt(acctPaneValues[1], 1, 1);
					acctPane.setValueAt(key, 1, 0);
					acctPane.setValueAt(value, 1, 1);
					//System.out.println(key + " " + value + " " + keyCounter + sdf.format(cal.getTime()));
				}
				if (key.equals("Leverage-S")){
				//if (keyCounter == 65){
					//acctPaneTitles.add(key);
					//acctPaneValues[acctPaneValuesCounter] = Double.parseDouble(value);
					//acctPaneValuesCounter = acctPaneValuesCounter + 1;
					//acctPane.setValueAt(acctPaneTitles.get(2), 2, 0);
					//acctPane.setValueAt(acctPaneValues[2], 2, 1);
					acctPane.setValueAt(key, 2, 0);
					acctPane.setValueAt(value, 2, 1);
					//System.out.println(key + " " + value + " " + keyCounter + sdf.format(cal.getTime()));
				}
				//if (keyCounter == 79){
				if (key.equals("MaintMarginReq")){
					//acctPaneTitles.add(key);
					//acctPaneValues[acctPaneValuesCounter] = Double.parseDouble(value);
					//acctPaneValuesCounter = acctPaneValuesCounter + 1;
					//acctPane.setValueAt(acctPaneTitles.get(3), 3, 0);
					//acctPane.setValueAt(acctPaneValues[3], 3, 1);
					acctPane.setValueAt(key, 3, 0);
					acctPane.setValueAt(value, 3, 1);
					//System.out.println(key + " " + value + " " + keyCounter + sdf.format(cal.getTime()));
				}
				//if (keyCounter == 106){
				if (key.equals("RealizedPnL")){
					//acctPaneTitles.add(key);
					//acctPaneValues[acctPaneValuesCounter] = Double.parseDouble(value);
					//acctPaneValuesCounter = acctPaneValuesCounter + 1;
					//acctPane.setValueAt(acctPaneTitles.get(4), 4, 0);
					//acctPane.setValueAt(acctPaneValues[4], 4, 1);
					acctPane.setValueAt(key, 4, 0);
					acctPane.setValueAt(value, 4, 1);
					//System.out.println(key + " " + value + " " + keyCounter + sdf.format(cal.getTime()));
				}
				//if (keyCounter == 92){
				if (key.equals("NetLiquidationByCurrency")){
					//acctPaneTitles.add(key);
					//accountCounter = accountCounter + 1;
					//acctPaneValues[acctPaneValuesCounter] = Double.parseDouble(value);
					//acctPaneValuesCounter = acctPaneValuesCounter + 1;
					//acctPane.setValueAt(acctPaneTitles.get(5), 5, 0);
					//acctPane.setValueAt(acctPaneValues[5], 5, 1);
					acctPane.setValueAt(key, 5, 0);
					acctPane.setValueAt(value, 5, 1);
					//System.out.println(key + " " + value + " " + keyCounter + sdf.format(cal.getTime()));
				}
				accountKeyCounter = accountKeyCounter + 1;
		
	}

	@Override
	public void updatePortfolio(Contract contract, Decimal position, double marketPrice, double marketValue,
			double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
		// Fills in the Portfolio table
		
		portPane.setValueAt(contract.symbol(), portfolioRowNumber, 0);
		portPane.setValueAt(contract.right(), portfolioRowNumber, 1);
		portPane.setValueAt(contract.strike(), portfolioRowNumber, 2);
		portPane.setValueAt(contract.lastTradeDateOrContractMonth(), portfolioRowNumber, 3);
		portPane.setValueAt(position, portfolioRowNumber, 4);
		portPane.setValueAt(marketPrice, portfolioRowNumber, 5);
		portPane.setValueAt(marketValue, portfolioRowNumber, 6);
		portPane.setValueAt(averageCost, portfolioRowNumber, 7);
		portPane.setValueAt(unrealizedPNL, portfolioRowNumber, 8);
		portPane.setValueAt(realizedPNL, portfolioRowNumber, 9);
		
		for (int i = 0; i < num_securities; i++){
			if (contract.localSymbol().equals(Security_data[i].ticker)){
				Security_data[i].contracts = Integer.valueOf(Util.decimalToStringNoZero(position));
			}
		}
		
	
		portfolioRowNumber++;
		if (portfolioRowNumber >= portPaneNumRows){
			portfolioRowNumber = 1;
		}
		
		debugPrint(844);
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
		
	}

	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub
		System.out.println(contractDetails.realExpirationDate());
		//int index = findIndexofTicker(contractDetails.marketName());
		int index = secDef_dict.get(reqId);
		int expiration_date = Integer.valueOf(contractDetails.realExpirationDate());
		
		if (expiration_date < min_expiration && expiration_date > todays_date_int) {
			min_expiration = expiration_date;
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
		// FIlls out the transaction panel
		
		transPane.setValueAt(contract.symbol(), transCounter, 0);
		transPane.setValueAt(execution.side(), transCounter, 1);
		transPane.setValueAt(contract.right(), transCounter, 2);
		transPane.setValueAt(contract.strike(), transCounter, 3);
		transPane.setValueAt(contract.lastTradeDateOrContractMonth(), transCounter, 4);
		transPane.setValueAt(execution.shares(), transCounter, 5);
		transPane.setValueAt(execution.avgPrice(), transCounter, 6);
		transPane.setValueAt(execution.time(), transCounter, 7);
		transCounter = transCounter + 1;
		if (transCounter >= transPaneNumRows){
			transCounter = 1;
		}
		
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
		
		// Convert the set of Expirations to an array
		int size_list = expirations.size();
		String expirations_array[] = new String[size_list];
		expirations_array = expirations.toArray(expirations_array);
		
		// Cycle through the list of expirations and 
		// If the temp_expiration matches today's date, return it in the strikes_list
		for (int i = 0; i < size_list; i++) {
			if (expirations_array[i].equals(todays_date)) {
				System.out.println("Got it on line 1437 Date = " + expirations_array[i]);
				
				// This converts the strikes into a list that that can be accessed back in the requesting function
				ArrayList<Double> temp_list = new ArrayList<Double>(strikes); 
				
				strikes_list.clear();
				min_expiration = Integer.valueOf(expirations_array[i]);
				strikes_list = temp_list;
				Security_data[conID_dict.get(reqId)].tradeclass = tradingClass;
			}
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
			System.out.println("Something went wrong on line 1112 " + conID);
		}
		
		return index;
	}
	
	public void debugPrint(int line) {
		System.out.println("Debugging on line " + line);
		for (int i = 0 ; i < num_securities; i++) {
			System.out.println(Security_data[i].ticker + " " +
		Security_data[i].conID + " " + Security_data[i].expiration +
		" " + Security_data[i].contracts);					
		}
	}
	
	public void debugPrint2(int line, int index, int expiration) {
		int l = Security_data[index].strikes.length;
		System.out.println("Debugging on line " + line);
		System.out.println("Expiration date for " + Security_data[index].ticker + " is " + expiration);
		for (int i = 0; i<l; i++) {
			System.out.println(Security_data[index].strikes[i]);
		}
	}

}