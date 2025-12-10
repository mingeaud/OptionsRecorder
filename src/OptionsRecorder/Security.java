package OptionsRecorder;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Security {
	String ticker, exchange, security_type, stock_ticker, stock_exchange, tradeclass, recommendation,
			put_recommend_string, call_recommend_string;
	int multiplier, conID, expiration, contracts, window, num_to_trade, sizer = 390 + 1, portfolio_line, port_call_line,
			port_put_line;
	double current_price, strikes[], requested_strikes[], data[][][][];
	double buy_call_cash_in, sell_call_cash_in, buy_put_cash_in, sell_put_cash_in;
	int buy_call_ADX_threshold, buy_call_delay, sell_call_ADX_threshold, sell_call_delay, buy_put_ADX_threshold;
	int buy_put_delay, sell_put_ADX_threshold, sell_put_delay;
	int time_index;
	int call_entry_time_index = 0;
	int put_entry_time_index = 0;
	int first_adx_counter = -1;
	int put_recommend_int = 0;
	int call_recommend_int = 0;
	boolean record_options, for_trading, already_traded, record_underlying;

	double[] dates;
	double temp1, temp2, temp3, temp4, temp5, temp6, temp7, temp8, temp9;

	double TR[] = new double[sizer];
	double DM1[] = new double[sizer];
	double negDM1[] = new double[sizer];
	double TR_num[] = new double[sizer];
	double DM_num[] = new double[sizer];
	double negDM_num[] = new double[sizer];
	double DI_num[] = new double[sizer];
	double negDI_num[] = new double[sizer];
	double DI_num_dif[] = new double[sizer];
	double DI_num_sum[] = new double[sizer];
	double DX[] = new double[sizer];
	double ADX_num[] = new double[sizer];
	double simpleMovAv[] = new double[sizer];
	double underlying[][] = new double[sizer + 1][4];

	// These are here to try to speed up organizing data to prevent the next price
	// from coming in too fast
	Calendar currentDate = Calendar.getInstance(Locale.ENGLISH);
	int hour, minute;

	public void initialize_data() {
		int l_1 = data.length;
		int l_2 = data[0].length;
		int l_3 = data[0][0].length;
		int l_4 = data[0][0][0].length;

		for (int i = 0; i < l_1; i++) {
			for (int j = 0; j < l_2; j++) {
				for (int k = 0; k < l_3; k++) {
					for (int l = 0; l < l_4; l++) {
						data[i][j][k][l] = 0;
					}
				}
			}
		}
	}

	public void initialize_underlying() {
		int l_5 = underlying.length;
		int l_6 = underlying[0].length;
		for (int i = 0; i < l_5; i++) {
			for (int j = 0; j < l_6; j++) {
				underlying[i][j] = 0;
			}
		}
	}

	public void process_strikes() {

		int len = strikes.length;

		double call = 0.0;
		int call_index = 0;
		double put = 0.0;
		int put_index = len;
		int span;
		int counter = 0;

		Set<Integer> hs = new HashSet<Integer>();

		for (int i = 1; i < len - 1; i++) {
			if (strikes[i] >= strikes[i - 1] && strikes[i] <= current_price) {
				call = strikes[i];
				call_index = i;
			}

			if (strikes[len - i - 1] <= strikes[len - i] && strikes[len - i - 1] >= current_price) {
				put = strikes[len - i - 1];
				put_index = len - i - 1;
			}
		}

		// If the current price lands right on a strike price this prevents the span
		// window from going past the window limit
		if (call_index == put_index) {
			call_index = call_index - 1;
		}

		while (window > len) {
			window = window / 2;
		}
		// System.out.println("debug line 60 " + call_index + " " + put_index + " " +
		// len);
		span = (window / 2) + 1;
		int good_strike = 0;
		for (int i = 0; i < len; i++) {
			good_strike = 0;
			if (strikes[i] == 522.5) { // debugging
				int fart = 0;
			}
			if (ticker.equals("SPY") || ticker.equals("QQQ")) {
				int j = (int) strikes[i];
				if (!hs.contains(j)) {
					good_strike = 1;
					hs.add(j);
				}
			} else {
				good_strike = 1;
			}
			if (i < call_index + span && i > put_index - span && good_strike == 1) {

				requested_strikes[counter] = strikes[i];

				// System.out.println("Counter=" + counter);
				counter++;
			}

		}
	}

	private int process_index() {
		// Find the index of the data array based on the hour and minute
		currentDate = Calendar.getInstance(Locale.ENGLISH); // Get the current date
		hour = currentDate.get(Calendar.HOUR_OF_DAY);
		minute = currentDate.get(Calendar.MINUTE);

		int index = (60 * (hour - 6)) + (minute - 30);

		return index;
	}

	private int process_cp_index(String call_put) {
		// If it's a Put use 0 as the first index, else use 1
		int cp_index = 0;

		if (call_put.equals("PUT")) {
			cp_index = 0;
		} else if (call_put.equals("CALL")) {
			cp_index = 1;
		} else {
			System.out.println("Something went wrong on line 67 of the Security file");
		}

		return cp_index;
	}

	public void process_underlying_price_data(double price) {

		// Find the indices for the time and call or put with these functions
		time_index = process_index();

		// This is account for any zeros that start the day which cause the ADX calc to
		// go way off
		if (first_adx_counter < 0) {
			first_adx_counter = time_index;
		}

		// If the time is between the trading hours, record it
		if (time_index >= 0 && time_index < sizer) {
			if (underlying[time_index][0] == 0) {
				underlying[time_index][0] = price;
				underlying[time_index][1] = price;
				underlying[time_index][2] = price;
				underlying[time_index][3] = price;
				for (int i = time_index; i < sizer - 1; i++) {
					underlying[i + 1][1] = price;
					underlying[i + 1][2] = price;
					underlying[i + 1][3] = price;
				}
			}

			// If this is a high, record it as the high
			if (price > underlying[time_index][1]) {
				underlying[time_index][1] = price;
			}

			// If this is the low, record it as the low
			if (price < underlying[time_index][2]) {
				underlying[time_index][2] = price;
			}

			// Always use the latest price as the close
			underlying[time_index][3] = price;

		}

		if (DI_num[time_index - 1] > negDI_num[time_index - 1] && ADX_num[time_index - 1] > buy_put_ADX_threshold) {
			if (put_recommend_int <= 0) {
				put_entry_time_index = time_index;
			}
			put_recommend_int = 1;
			put_recommend_string = "BUY";
		} else if (DI_num[time_index - 1] < negDI_num[time_index - 1]
				&& ADX_num[time_index - 1] > sell_put_ADX_threshold) {
			if (put_recommend_int >= 0) {
				put_entry_time_index = time_index;
			}
			put_recommend_int = -1;
			put_recommend_string = "SELL";
		} else {
			if (put_recommend_int != 0) {
				put_entry_time_index = time_index;
			}
			put_recommend_int = 0;
			put_recommend_string = "OUT";
		}

		if (DI_num[time_index - 1] > negDI_num[time_index - 1] && ADX_num[time_index - 1] > sell_call_ADX_threshold) {
			if (call_recommend_int >= 0) {
				call_entry_time_index = time_index;
			}
			call_recommend_int = -1;
			call_recommend_string = "SELL";
		} else if (DI_num[time_index - 1] < negDI_num[time_index - 1]
				&& ADX_num[time_index - 1] > buy_call_ADX_threshold) {
			if (call_recommend_int <= 0) {
				call_entry_time_index = time_index;
			}
			call_recommend_int = 1;
			call_recommend_string = "BUY";
		} else {
			if (call_recommend_int != 0) {
				call_entry_time_index = time_index;
			}
			call_recommend_int = 0;
			call_recommend_string = "OUT";
		}
	}

	public void process_price_data(String call_put, double strike_index, double price, int bid_ask) {

		// Find the indices of for the time and call or put with these functions
		time_index = process_index();

		// System.out.println("Sec Func line 107 hour = " + hour + " " + " minute = " +
		// minute + " index = " + time_index + " " + price);

		if (time_index >= 0 && time_index <= 6.5 * 60) {
			int cp_index = process_cp_index(call_put);

			// If this is the bid price
			if (bid_ask == 0) {
				// If no data has been entered for this bar, this is the open
				if (data[cp_index][(int) strike_index][time_index][0] == 0) {
					data[cp_index][(int) strike_index][time_index][0] = price;
					data[cp_index][(int) strike_index][time_index][1] = price;
					data[cp_index][(int) strike_index][time_index][2] = price;
					data[cp_index][(int) strike_index][time_index][3] = price;

				}

				// If this is a high, record it as the high
				if (price > data[cp_index][(int) strike_index][time_index][1]) {
					data[cp_index][(int) strike_index][time_index][1] = price;
				}

				// If this is the low, record it as the low
				if (price < data[cp_index][(int) strike_index][time_index][2]) {
					data[cp_index][(int) strike_index][time_index][2] = price;
				}

				// Always use the latest price as the close
				data[cp_index][(int) strike_index][time_index][3] = price;
			}

			// If this is the ask price
			else {
				// If no data has been entered for this bar, this is the open
				if (data[cp_index][(int) strike_index][time_index][4] == 0) {
					data[cp_index][(int) strike_index][time_index][4] = price;
					data[cp_index][(int) strike_index][time_index][5] = price;
					data[cp_index][(int) strike_index][time_index][6] = price;
					data[cp_index][(int) strike_index][time_index][7] = price;
				}

				// If this is a high, record it as the high
				if (price > data[cp_index][(int) strike_index][time_index][5]) {
					data[cp_index][(int) strike_index][time_index][5] = price;
				}

				// If this is the low, record it as the low
				if (price < data[cp_index][(int) strike_index][time_index][6]) {
					data[cp_index][(int) strike_index][time_index][6] = price;
				}

				// Always use the latest price as the close
				data[cp_index][(int) strike_index][time_index][7] = price;

			}

		}

	}

	public void process_delta_data(String call_put, double strike_index, double delta, double undPrice) {

		// Find the indices of for the time and call or put with these functions
		time_index = process_index();

		if (time_index >= 0 && time_index <= 6.5 * 60) {
			int cp_index = process_cp_index(call_put);
			data[cp_index][(int) strike_index][time_index][8] = delta;
			data[cp_index][(int) strike_index][time_index][9] = undPrice;
		}
	}

	// Calculate the Hull Moving Average
	void calcHullMovingAverage(int warmup, int hull, int rows, float data[], float avgShort[], float avgLong[],
			float target[]) {

		int avgDivBy2 = (int) Math.round((float) hull / 2.0);
		int avgSqrt = (int) Math.round((float) Math.sqrt(hull));

		weightedMovingAvgOneDim(rows, avgDivBy2, data, avgShort);
		weightedMovingAvgOneDim(rows, hull, data, avgLong);

		for (int i = 0; i < rows; i++) {
			avgLong[i] = (2 * avgShort[i]) - avgLong[i];
		}

		weightedMovingAvgOneDim(rows, avgSqrt, avgLong, target);
	}

	void weightedMovingAvgOneDim(int rows, int wAvg, float data[], float target[]) {

		int back_steps = 0;
		float answer = 0;
		float weight = 0;
		target[0] = 0;

		for (int i = 1; i <= wAvg; i++) {
			weight = weight + i;
		}

		for (int i = 1; i < rows; i++) {
			back_steps = 0;
			answer = 0;
			if (i <= wAvg) {
				back_steps = i + 1;
			} else {
				back_steps = wAvg;
			}
			answer = 0;
			for (int j = 0; j < back_steps; j++) {
				answer = answer + (data[i - j] * (float) (wAvg - j) / weight);
			}
			target[i] = answer;

		}

	}

	void adx(int trailing_num) {

		if (first_adx_counter < 0) {
			return;
		}
		// Reset the working space just in case (this might not be needed)
		for (int i = 0; i < sizer; i++) {
			TR[i] = 0;
			DM1[i] = 0;
			negDM1[i] = 0;
			TR_num[i] = 0;
			DM_num[i] = 0;
			negDM_num[i] = 0;
			DI_num[i] = 0;
			negDI_num[i] = 0;
			DI_num_dif[i] = 0;
			DI_num_sum[i] = 0;
			DX[i] = 0;
			ADX_num[i] = 0;
		}

		// %% Calculates TR
		TR[1] = 0;
		for (int i = first_adx_counter + 1; i < sizer; i++) {
			temp1 = underlying[i][1];
			temp2 = underlying[i][2];
			temp3 = underlying[i - 1][3];
			temp4 = temp1 - temp2;
			temp5 = Math.abs(temp1 - temp3);
			temp6 = Math.abs(temp2 - temp3);
			temp8 = Math.max(temp4, temp5);
			temp9 = Math.max(temp5, temp6);
			temp7 = Math.max(temp8, temp9);
			TR[i] = temp7;

			temp1 = underlying[i][1] - underlying[i - 1][1];
			temp2 = underlying[i - 1][2] - underlying[i][2];

			if (temp1 > temp2) {
				temp3 = Math.max(temp1, 0);
				DM1[i] = temp3;
			} else {
				DM1[i] = 0;
			}
			// System.out.println(temp1+ " " + temp2+ " " + temp3 + " " + DM1[i]);

			temp1 = underlying[i - 1][2] - underlying[i][2];
			temp2 = underlying[i][1] - underlying[i - 1][1];
			if (temp1 > temp2) {
				negDM1[i] = Math.max(temp1, 0);
			} else {
				negDM1[i] = 0;
			}
			// System.out.println(negDM1[i]);
		}

		// calculates TR_num
		temp1 = 0;
		for (int i = first_adx_counter + 1; i <= trailing_num + first_adx_counter; i++) {
			temp1 = temp1 + TR[i];
		}

		TR_num[trailing_num + first_adx_counter] = temp1;

		for (int i = (first_adx_counter + trailing_num + 1); i < sizer; i++) {
			TR_num[i] = TR_num[i - 1] - (TR_num[i - 1] / trailing_num) + TR[i];
			// System.out.println(TR_num[i]);
		}

		// clear temp1;

		// Calculates DM_num
		temp1 = 0;
		for (int i = first_adx_counter + 1; i <= trailing_num + first_adx_counter; i++) {
			temp1 = temp1 + DM1[i];
		}

		DM_num[trailing_num + first_adx_counter] = temp1;

		for (int i = (trailing_num + 1 + first_adx_counter); i < sizer; i++) {
			DM_num[i] = DM_num[i - 1] - (DM_num[i - 1] / trailing_num) + DM1[i];
			// System.out.println(DM_num[i]);
		}

		// clear temp1;

		// Calculates negDM_num
		temp1 = 0;
		for (int i = 1 + first_adx_counter; i <= trailing_num + first_adx_counter; i++) {
			temp1 = temp1 + negDM1[i];
		}

		negDM_num[trailing_num + first_adx_counter] = temp1;

		for (int i = trailing_num + 1 + first_adx_counter; i < sizer; i++) {
			negDM_num[i] = negDM_num[i - 1] - (negDM_num[i - 1] / trailing_num) + negDM1[i];
			// System.out.println(negDM_num[i]);
		}

		// clear temp1;

		// Calculates DI_num
		for (int i = trailing_num + first_adx_counter; i < sizer; i++) {

			if (TR_num[i] == 0) {
				DI_num[i] = 0;
			} else {
				DI_num[i] = 100 * DM_num[i] / TR_num[i];
			}
		}

		// Calculates negDI_num
		for (int i = trailing_num + first_adx_counter; i < sizer; i++) {

			if (TR_num[i] == 0) {
				negDI_num[i] = 0;
			} else {
				negDI_num[i] = 100 * negDM_num[i] / TR_num[i];
			}
			// System.out.println(negDI_num[i]);
		}

		// calculates DI_num_dif
		for (int i = 1 + first_adx_counter; i < sizer; i++) {
			DI_num_dif[i] = Math.abs(DI_num[i] - negDI_num[i]);
			// System.out.println(DI_num_dif[i]);
		}

		// Calculates DI_num_sum
		for (int i = 1 + first_adx_counter; i < sizer; i++) {
			DI_num_sum[i] = DI_num[i] + negDI_num[i];
		}

		// calculates DX
		for (int i = 1 + first_adx_counter; i < sizer; i++) {
			if (DI_num_sum[i] == 0) {
				DX[i] = 0;
			} else {
				DX[i] = 100 * DI_num_dif[i] / DI_num_sum[i];
			}
		}

		temp1 = 0;
		// Calculates ADX
		for (int i = trailing_num + first_adx_counter; i < 2 * trailing_num; i++) {
			temp1 = temp1 + DX[i];
		}

		ADX_num[2 * trailing_num - 1] = temp1 / trailing_num;
		for (int i = (2 * trailing_num) + first_adx_counter; i < sizer; i++) {
			ADX_num[i] = (ADX_num[i - 1] * (trailing_num - 1) + DX[i]) / trailing_num;
			// System.out.println(ADX_num[i]);
		}
	}
}
