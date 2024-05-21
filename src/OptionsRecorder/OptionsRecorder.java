package OptionsRecorder;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

//import OptionsRecorder.FrontPanel;

public class OptionsRecorder {

	public static void main(String[] args) throws FileNotFoundException {
		// TODO Auto-generated method stub
		
		try {
            // Set cross-platform Java L&F (also called "Metal")
			UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    } 
    catch (UnsupportedLookAndFeelException e) {
       // handle exception
    }
    catch (ClassNotFoundException e) {
       // handle exception
    }
    catch (InstantiationException e) {
       // handle exception
    }
    catch (IllegalAccessException e) {
       // handle exception
    }
		
	FrontPanel mainLoop = new FrontPanel();
		
	}

}
