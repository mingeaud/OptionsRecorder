package OptionsRecorder;

public class Security {
	String ticker, exchange, security_type;
	int multiplier, conID, expiration;
	double current_price, strikes[], requested_strikes[], data[][][][];
	
	
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
	
	public void process_strikes(int window) {
		
		
		int len = strikes.length;
		
		double call = 0.0;
		int call_index = 0;
		double put = 0.0;
		int put_index = len;
		int span;
		int counter = 0;
		
		for (int i = 1; i < len - 1; i++){
		    if (strikes[i] >= strikes[i - 1] && strikes[i] <= current_price) {
		        call = strikes[i];
		        call_index = i;
		    }


		    if (strikes[len - i - 1] <= strikes[len - i] && strikes[len - i - 1] >= current_price) {
		        put = strikes[len - i - 1];
		        put_index = len - i - 1;
		    }
		}
		
		while (window > len) {
			window = window / 2;
		}
				   
		span = (window / 2) + 1;
		for (int i = 0; i < len; i++) {
		    if (i < call_index + span && i > put_index - span) {
		    	requested_strikes[counter] = strikes[i];
		    	counter++;
		    }
		
		}
	}
	
	private int process_index(int hour, int minute) {
		// Find the index of the data array based on the hour and minute
		
		int index = (60*(hour - 6)) + (minute - 30);
		
		return index;
	}
	
	private int process_cp_index(String call_put) {
		// If it's a Put use 0 as the first index, else use 1
		int cp_index = 0;
		
		if (call_put.equals("PUT")) {
			cp_index = 0;			
		}
		else if (call_put.equals("CALL")) {
			cp_index = 1;
		}
		else {
			System.out.println("Something went wrong on line 67 of the Security file");
		}
		
		return cp_index;
	}
	
	
	public void process_price_data(String call_put, int hour, int minute, double strike_index, double price) {
		
		// Find the indices of for the time and call or put with these functions
		int time_index = process_index(hour, minute);
		time_index = 0;
		
		if (time_index >=0 && time_index <= 23*60) {
			int cp_index = process_cp_index(call_put);
			// If no data has been entered for this bar, this is the open
			if (data[cp_index][(int)strike_index][time_index][0] == 0 &&
					data[cp_index][(int)strike_index][time_index][1] == 0 &&
					data[cp_index][(int)strike_index][time_index][2] == 0 &&
					data[cp_index][(int)strike_index][time_index][3] == 0) {
				data[cp_index][(int)strike_index][time_index][0] = price;
			}
			
			// If this is a high, record it as the high
			if (price > data[cp_index][(int)strike_index][time_index][1]) {
				data[cp_index][(int)strike_index][time_index][1] = price;
			}
			
			// If this is the low, record it as the low
			if (data[cp_index][(int)strike_index][time_index][2] == 0 ||
					price < data[cp_index][(int)strike_index][time_index][2]) {
				data[cp_index][(int)strike_index][time_index][2] = price;
			}
			
			// Always use the latest price as the close
			data[cp_index][(int)strike_index][time_index][3] = price;
		}
		
	}
	
	public void process_delta_data(String call_put, int hour, int minute, double strike_index, double delta, double undPrice) {
		// Find the indices of for the time and call or put with these functions
		int time_index = process_index(hour, minute);
		time_index = 0;

		if (time_index >=0 && time_index <= 23*60) {
			int cp_index = process_cp_index(call_put);
			data[cp_index][(int)strike_index][time_index][4] = delta;
			data[cp_index][(int)strike_index][time_index][5] = undPrice;
		}		
	}
	
	// Calculate the Hull Moving Average
	void calcHullMovingAverage(int warmup, int hull, int rows, float data[], float avgShort[], float avgLong[], float target[]) {

	    int avgDivBy2 = (int) Math.round((float)hull / 2.0);
	    int avgSqrt = (int) Math.round((float)Math.sqrt(hull));

	    weightedMovingAvgOneDim(rows, avgDivBy2, data, avgShort);
	    weightedMovingAvgOneDim(rows, hull, data, avgLong);

	    for (int i = 0; i < rows; i++) {
	        avgLong[i] = (2 * avgShort[i]) - avgLong[i];
	    }

	    weightedMovingAvgOneDim(rows, avgSqrt, avgLong, target);
	}
	
	void weightedMovingAvgOneDim(int rows, int wAvg, float data[], float target[]) {

	    int back_steps = 0;
	    float answer = 0.0;
	    float weight = 0.0;
	    target[0] = 0;


	    for (int i = 1; i <= wAvg; i++) {
	        weight = weight + i;
	    }

	    for (int i = 1; i < rows; i++) {
	        back_steps = 0;
	        answer = 0.0;
	        if (i <= wAvg) {
	            back_steps = i + 1;
	        }
	        else {
	            back_steps = wAvg;
	        }
	        answer = 0;
	        for (int j = 0; j < back_steps; j++) {
	            answer = answer + (data[i - j] * (float)(wAvg - j) / weight);
	        }
	        target[i] = answer;

	    }

	}
}
