package OptionsRecorder;

public class Security {
	String ticker, exchange, security_type;
	int multiplier, conID, expiration;
	double current_price, strikes[], requested_strikes[];
	
	
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
}
