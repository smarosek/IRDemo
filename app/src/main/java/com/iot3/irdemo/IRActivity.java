package com.iot3.irdemo;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import net.calit2.mooc.iot_db410c.db410c_gpiolib.GpioProcessor;

/*
 *  Created by Susan Marosek, 20 July, 2016
 *  Coursera class: IoT - Sensing and Actuation of Devices (UCSD), Week 4. Module 6. IR Remote Demo
 *
 * Before running this code on the DragonBoard, make sure that DEBUG (in code) is set to false,
 * otherwise the GPIO methods will not be called and your other devices will not work.
 *
 * Once loaded onto the DragonBoard, here is how to use this app:
 *  - Select the Calibrate, Stepper Motor or PIR radio button (definitely should Calibrate your
 *      remote first, otherwise the function selections will most likely be incorrect. Once you
 *      know your remote's average pulse value, you can change the DEFAULT_AVE_PULSE (in code)
 *     value to reflect your remote's average pulse.
 *
 *  - To Calibrate:
 *     - Select the Calibrate radio button.
 *     - Press the GO button near top right side of screen
 *     - Choose a button on your IR Remote to use as your function select button and press it
 *         5 times (your have approx. 5 seconds to do this). (A prompt will appear in upper
 *         right corner of screen telling you what to do in case you forget.)
 *     - Once the Calibration task has completed, the average pulse will be displayed on the screen.
 *     - It is highly suggested that you run Calibrate more than once to ensure an accurate
 *         average pulse calculation.
 *     - When satisfied with the average pulse value, move on to another function. NOTE: This
 *         average pulse value WILL NOT be retained once the app has been exited. You will need
 *         to run Calibrate each time the app is started OR you may modify the code to reflect
 *         your IR Remote's average pulse.
 *
 * - To Run the Stepper Motor (NOTE this is coded to the Stepper Motor in the BOM for this class).
 *      - Select the Stepper Motor radio button.
 *      - Select the desired Clockwise or Counter-Clockwise radio button.
 *      - Press the GO button near top right side of screen.
 *      - Press your IR Remote button (that was used in the Calibration step) once, twice or 3 times
 *          to select the rotation angle, 90, 180 or 360 degrees respectively.
 *          (You have approx. 5 seconds to select the sub-function)
 *      - The button for the selected rotation angle will be highlighted once selected and the
 *          stepper motor will complete the rotation.
 *      - NOTE: angle value buttons are NOT active. Pressing them will be a No-Op.
 *
 *  - To Run the PIR:
 *      - Select the PIR radio button.
 *      - Press the GO button near top right side of screen.
 *      - Press the IR Remote button once to turn ON the PIR sensor or twice to turn it OFF.
 *      - Assuming you have hooked up the PIR sensor as described in class:
 *          - an LED will turn on when the PIR sensor is turned on and
 *          - a separate LED will turn on then off when the PIR sensor detects motion
 *              AND the PIR sensor is ON.
 */
public class IRActivity extends Activity
{
    static final String tag = "IOT3";
    static final boolean DEBUG = false;     // Set to true to run in emulator mode & test logic only
    static final boolean DEBUGV = false;    // Set true for very verbose log messages

    // Demo Function Options
    static final int RUN_CALIBRATE = 1;
    static final int RUN_STEPPER_MOTOR = 2;
    static final int RUN_PIR = 3;

    // Demo Sub-Option
    static final int RUN_SM_90 = 1;
    static final int RUN_SM_180 = 2;
    static final int RUN_SM_360 = 3;
    static final int RUN_PIR_ON = 1;
    static final int RUN_PIR_OFF = 2;

    static final int DEFAULT_AVE_PULSE = 80;

    // Overall Demo Data
    GpioProcessor gpioProcessor;
    // Stepper Motor Controls
    GpioProcessor.Gpio blueWire;
    GpioProcessor.Gpio pinkWire;
    GpioProcessor.Gpio yellowWire;
    GpioProcessor.Gpio orangeWire;
    // PIR GPIO
    GpioProcessor.Gpio vcc;
    // Indicator GPIO
    GpioProcessor.Gpio ind;
    // IR Input
    GpioProcessor.Gpio dIn;

    // Tasks and Handlers
    CalibrateRemoteAsyncTask calTask;
    RemoteClickCountAsyncTask rccTask;
    Handler calibrateTimeoutHandler;
    Handler demoTimeoutHandler;

    // Demo Controls
    Button goButton;
    TextView resultTV;
    // Stepper Motor GUI controls
    Button x90_deg_button;
    Button x180_deg_button;
    Button x360_deg_button;
    // PIR GUI Controls
    Switch pirSwitch;
    TextView pirStatus;

    int origColor;
    int option = RUN_CALIBRATE;

    /* IR Remote Calibration Data */
        //  Average Pulse
        int avePulse = DEFAULT_AVE_PULSE;


    /* Stepper Motor Data */
        static final int DELAY = 1;  // Delay Time for stepper motor
        // See HANNAH's comment on the BOM stepper motor order page of Amazon:
        // https://www.amazon.com/TOOGOO-28BYJ-48-28BYJ48-4-Phase-Stepper/dp/B00H8KVDHE?ie=UTF8&keywords=stepper%20motor%2028byj-48&qid=1435249378&ref_=sr_1_4&sr=8-4
        int rev1 = 64;  // 64 gears / revolution
        // Also here are 32 steps = 8 steps in each sequence * 4 wire settings per step

        // Forward (Clockwise) Sequence - BOM Stepper Motor
        int [][] SS_F = new int [][] { {0,0,0,1},{0,0,1,1},{0,0,1,0}, {0,1,1,0},
                {0,1,0,0}, {1,1,0,0}, {1,0,0,0}, {1,0,0,1} };

        // Reverse (Counter-Clockwise) Sequence - BOM Stepper Motor
        int [][] SS_R = new int [][] { {0,0,0,1},{1,0,0,1},{1,0,0,0}, {1,1,0,0},
                {0,1,0,0}, {0,1,1,0}, {0,0,1,0}, {0,0,1,1} };

        // Stepper motor rotation: 0 = clockwise, 1 = counter-clockwise
        int direction = 0;

    @Override
    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_ir );

        // GUI components
        RadioGroup irRadioGroup;    // RadioGroup for Calibrate vs Execute options
        RadioGroup dirRadioGroup;   // RadioGroup for Stepper Moter direction

        goButton = (Button)findViewById( R.id.go_btn );
        resultTV = (TextView) findViewById( R.id.ave_pulse_result_tv );

        // PIR GUI Components
        pirSwitch = (Switch) findViewById( R.id.pir_switch );
        pirStatus = (TextView) findViewById( R.id.pir_status );

        // Stepper Motor GUI
        x90_deg_button = (Button)findViewById( R.id.x90_deg );
        x180_deg_button = (Button)findViewById( R.id.x180_deg );
        x360_deg_button = (Button)findViewById( R.id.x360_deg );

        origColor = Color.LTGRAY;  // Saving original button color

        // Thread / task data
        calibrateTimeoutHandler = new Handler();
        demoTimeoutHandler = new Handler();

        // GPIO / DragonBoard
        gpioProcessor = new GpioProcessor();
        // IR Remote GPIO
        dIn = gpioProcessor.getPin27();         // IR transmitter pin27
        // Stepper Motor GPIOs
        blueWire = gpioProcessor.getPin34();     // Blue
        pinkWire = gpioProcessor.getPin24();     // Pink
        yellowWire = gpioProcessor.getPin33();   // Yellow
        orangeWire = gpioProcessor.getPin26();   // Orange
        // PIR GPIO
        vcc = gpioProcessor.getPin29();     // Wire connecting pin29 to VCC of PIR
        // Indicator GPIO
        ind = gpioProcessor.getPin32();     // Changed from 30 to 32

        if ( !DEBUG )
        {   // Set GPIO data direction
            dIn.in();
            blueWire.out();
            pinkWire.out();
            yellowWire.out();
            orangeWire.out();
            vcc.out();
            ind.out();
        }
        else
        {   Log.d( tag, " ");
            Log.d( tag, "DEBUG so not setting up GPIOs");
        }

        // Handle Demo Option Radio Buttons
        irRadioGroup = (RadioGroup)findViewById( R.id.ir_demo_options );
        irRadioGroup.setOnCheckedChangeListener( new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged( RadioGroup group, int checkedId )
            {
                switch ( checkedId )
                {
                    case R.id.calibrate_rb:
                        option = RUN_CALIBRATE;
                        ind.low();
                        break;
                    case R.id.stepper_motor_rb:
                        option = RUN_STEPPER_MOTOR;
                        ind.high();
                        break;
                    case R.id.pir_rb:
                        option = RUN_PIR;
                        ind.high();
                        break;
                    default:
                        option = RUN_CALIBRATE;
                        ind.low();
                        break;
                }
            }
        } );

        // Handle Stepper Motor Direction radio button clicks
        dirRadioGroup = (RadioGroup)findViewById( R.id.stepper_motor_rg );
        dirRadioGroup.setOnCheckedChangeListener( new RadioGroup.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged( RadioGroup group, int checkedId )
            {
                if ( checkedId == R.id.cw )
                    direction = 0;
                else
                    direction = 1;
            }
        } );


        // Define PIR switch listener
        pirSwitch.setChecked( false );
        pirSwitch.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged( CompoundButton compoundButton, boolean bChecked )
            {
                if ( bChecked )
                {   pirStatus.setText( R.string.pir_status_on );
                    if ( !DEBUG )
                        vcc.high();
                }
                else
                {   pirStatus.setText( R.string.pir_status_off );
                    if ( !DEBUG )
                        vcc.low();
                }
            }
        });

        if ( pirSwitch.isChecked())
        {
            pirStatus.setText( R.string.pir_status_on );
            if ( !DEBUG )
                vcc.high();
        }
        else
        {
            pirStatus.setText( R.string.pir_status_off );
            if ( !DEBUG )
                vcc.low();
        }
    }


    /**
     * onResume
     */
    @Override
    protected void onResume()
    {
        super.onResume();
        Log.d( tag, "In onResume" );
    }

    /**
     * onPause
     */
    @Override
    protected void onPause()
    {
        super.onPause();
        cleanup();
    }


    /*
     *  Run on GO button click
     */
    public void OnGoButtonClick( View view )
    {
        // Reset GUI components to original colors
        ResetSubOptionGUI();

        if ( DEBUG )
            Log.d( tag, "In OnGOButtonClick (goBtn clicked)");

        switch ( option )
        {
            case RUN_CALIBRATE:
                if ( DEBUG )
                    Log.d( tag, "Run CALIBRATE" );

                RunCalibrate();
                break;
            case RUN_STEPPER_MOTOR:
            case RUN_PIR:
                RunDemo();
                break;
            default:
                break;
        }
    }


    /*
     *  Called when GO Button is pressed AND the Calibrate radio button option is checked
     */
    private void RunCalibrate ()
    {
        final Toast calToast = Toast.makeText( getBaseContext(),
                "Please click remote button 5 times.", Toast.LENGTH_LONG);
        calToast.setGravity( Gravity.TOP | Gravity.RIGHT, 10, 50 );
        calToast.show();

        // Start the calibration task
        calTask = new CalibrateRemoteAsyncTask();
        calTask.execute( dIn );

        /*
         * Sets up a timeout for the calibration task (5 seconds)
         *
         * Taken from:
         * http://stackoverflow.com/questions/7882739/android-setting-a-timeout-for-an-asynctask
         */
        calibrateTimeoutHandler.postDelayed( new Runnable()
        {
            @Override
            public void run()
            {
                if ( DEBUG )
                    Log.d( tag, "In calibrateTimeoutHandler to cancel calibration task" );

                if ( calTask.getStatus() == AsyncTask.Status.RUNNING )
                {
                    calTask.cancel( true );
                }
            }
        }, 5000 );
    }


    /*
     *  Called when GO button is pressed AND Calibrate radio button is NOT selected.
     *  Will execute a task to collect IR Remote button click info.
     */
    public void RunDemo()
    {
        final Toast toast = Toast.makeText( getBaseContext(),
                "Click remote button to run appropriate demo sub-option", Toast.LENGTH_LONG);
        toast.setGravity( Gravity.TOP | Gravity.RIGHT, 10, 50 );
        toast.show();

        // Start the demo click counter task
        rccTask = new RemoteClickCountAsyncTask();
        rccTask.execute( dIn );

        /*
         * Sets up a timeout for the remote click count task (5 seconds)
         *
         * Taken from:
         * http://stackoverflow.com/questions/7882739/android-setting-a-timeout-for-an-asynctask
         */
        demoTimeoutHandler.postDelayed( new Runnable()
        {
            @Override
            public void run()
            {
                if ( DEBUG )
                    Log.d( tag, "In demoTimeoutHandler to cancel remote click count task" );

                if ( rccTask.getStatus() == AsyncTask.Status.RUNNING )
                {
                    rccTask.cancel( true );
                }
            }
        }, 5000 );
    }

    /*
     *  Called from RemoteClickCountAsyncTask once the user has selected the desired sub-option
     *  with the IR Remote.
     */
    public void RunSelectedOption( int subOption )
    {
        switch ( option )
        {
            case RUN_STEPPER_MOTOR:
                if ( DEBUG )
                    Log.d( tag, "Run STEPPER_MOTOR" );

                onStepperMotor( subOption );
                break;
            case RUN_PIR:
                if ( DEBUG )
                    Log.d( tag, "Run PIR" );

                onPIR( subOption );
                break;

            default:
                Log.d( tag, "Unknown selection" );
                break;
        }
    }

    /*
     *  Called to execute the specific Stepper Motor sub-functions in response to the
     *  user's IR Remote clicks.
     */
    public void onStepperMotor( int subOption )
    {
        final Toast toastLow = Toast.makeText( getBaseContext(),
                "onStepperMotor -- subOption value TOO LOW", Toast.LENGTH_SHORT);
        toastLow.setGravity( Gravity.TOP | Gravity.RIGHT, 10, 50 );

        final Toast toastHi = Toast.makeText( getBaseContext(),
                "onStepperMotor -- subOption value TOO HIGH", Toast.LENGTH_SHORT);
        toastHi.setGravity( Gravity.TOP | Gravity.RIGHT, 10, 50 );

        // Got user's selection through IR Remote so now figure out which sub-option to run
        int deg = 0;
        int threshold = (int)(avePulse*.5);
        Log.d( tag, "subOption = " + subOption + "   threshold = " + threshold );

        if ( subOption < (int)(.5*avePulse))
        {   // subOption value is too low or possibly 0, so can't determine user selection
            Log.d( tag, "In onStepperMotor -- subOption value TOO LOW = "+ subOption );
            toastLow.show();
        }
        else if ( subOption <= (RUN_SM_90*avePulse + threshold) )
        {   // Run 90 degrees
            Log.d( tag, "onStepperMotor rotate 90 degrees" );
            x90_deg_button.setBackgroundColor( Color.MAGENTA );
            deg = 90;
        }
        else if ( subOption <= (RUN_SM_180*avePulse + threshold) )
        {   // Run 180 degrees
            Log.d( tag, "onStepperMotor rotate 180 degrees" );
            x180_deg_button.setBackgroundColor( Color.MAGENTA );
            deg = 180;
        }
        else if ( subOption <= (RUN_SM_360*avePulse + threshold) )
        {   // Run 360 degrees
            Log.d( tag, "onStepperMotor rotate 360 degrees" );
            x360_deg_button.setBackgroundColor( Color.MAGENTA );
            deg = 360;
        }
        else
        {   // subOption value is too High or possibly 0, so can't determine user selection
            Log.d( tag, "In onStepperMotor -- counter value TOO HIGH = "+ subOption );
            toastHi.show();
        }
        if ( deg > 0 )
            RunStepperMotor( deg );
    }

    /*
     *  Called to execute the specific PIR sub-functions in response to the
     *  user's IR Remote clicks.
     */
    public void onPIR( int subOption  )
    {   final Toast toastLow = Toast.makeText( getBaseContext(),
            "onPIR -- subOption value TOO LOW", Toast.LENGTH_SHORT);
        toastLow.setGravity( Gravity.TOP | Gravity.RIGHT, 10, 50 );

        final Toast toastHi = Toast.makeText( getBaseContext(),
                "onPIR -- subOption value TOO HIGH", Toast.LENGTH_SHORT);
        toastHi.setGravity( Gravity.TOP | Gravity.RIGHT, 10, 50 );

        int threshold = (int)(avePulse*.5);
        Log.d( tag, "subOption = " + subOption + "   threshold = " + threshold );

        if ( subOption < (int)(.5*avePulse))
        {
            // subOption value is too Low or possibly 0, so can't determine user selection
            Log.d( tag, "In onPIR -- subOption value TOO LOW = "+ subOption );
            toastLow.show();
        }
        else if ( subOption <= (RUN_PIR_ON*avePulse + threshold) )
        {
            // Set PIR ON
            Log.d( tag, "onPIR set PIR ON" );
            pirSwitch.setChecked( true );
        }
        else if ( subOption <= (RUN_PIR_OFF*avePulse + threshold) )
        {
            // Set PIR ON
            Log.d( tag, "onPIR set PIR OFF" );
            pirSwitch.setChecked( false );
        }
        else
        {
            // subOption value is too High or possibly 0, so can't determine user selection
            Log.d( tag, "In onPIR -- counter value TOO HIGH = "+ subOption );
            toastHi.show();
        }
    }


    /*
     * Sends commands to stepper motor to rotate user specified direction and angle
     */
    public void RunStepperMotor( int deg )
    {   final int degrees = deg;

        new Thread( new Runnable()
        {
            @Override
            public void run()
            {
                Log.d( tag,"Running RunStepperMotor for degrees = "+degrees );

                int sets = 8;   // number of sets in the sequence

                // x is number of steps for the desired angle
                int x = (int)((double)(degrees/360.)*rev1*sets);

                // Set the Sequence array to traverse based on the selected direction
                int [][] SEQ;

                if ( direction == 0 )
                    SEQ = SS_F;
                else
                    SEQ = SS_R;

                Log.d( tag, "direction = "+ direction);
                Log.d( tag, "degrees = "+ degrees + "    x = "+ x );

                // For loop for number of times through the loop to turn motor the specified
                // number of degrees.
                for ( int j = 0; j < x; j++ )
                {
                    if ( DEBUG && DEBUGV )
                    {   Log.d( tag, " " );
                        Log.d( tag, "J - " + j );
                    }

                    // for loop to move through stepper motor sequence
                    for ( int i = 0; i < sets; i++ )
                    {
                        if ( DEBUG && DEBUGV )
                            Log.d( tag, "set " + i );

                        // Set GPIOs to appropriate sequence values
                        setGpio( blueWire, SEQ[i][0] );
                        delay( DELAY );

                        setGpio( pinkWire, SEQ[i][1] );
                        delay( DELAY );

                        setGpio( yellowWire, SEQ[i][2] );
                        delay( DELAY );

                        setGpio( orangeWire, SEQ[i][3] );
                        delay( DELAY );

                        if ( DEBUG && DEBUGV )
                        {   Log.d( tag, "blueWire = " + SEQ[i][0] + "     " +
                                        "pinkWire = " + SEQ[i][1] + "     " +
                                        "yellowWire = " + SEQ[i][2] + "     " +
                                        "orangeWire = " + SEQ[i][3] );
                        }
                    } //end for i loop
                } // end for j loop

                Log.d( tag, "Done with loops" );
                cleanup();
            }
        } ).start();
    }

    //  Just a short utility function to make setting stepper motor GPIOs easier
    public void setGpio( GpioProcessor.Gpio gpioVal, int ssVal )
    {   int LOW = 0;
        int HIGH = 1;

        if ( DEBUG )    // Don't set if we're in DEBUG mode
            return;

        if ( ssVal == LOW )
            gpioVal.low();
        else
            gpioVal.high();
        return;
    }

    // Short utility function to make stepper motor delay easier
    public void delay( int d )
    {   try
        {
            Thread.sleep( d );
        }
        catch ( InterruptedException e )
        {
            e.printStackTrace();
        }
    }

    /*
     *  Reset GUI subOptions to original colors / states.
     *  Called when GO button is pressed.
     */
    private void ResetSubOptionGUI()
    {
        Log.d( tag, "In ResetSubOptionGUI" );

        x90_deg_button.setBackgroundColor( origColor );
        x180_deg_button.setBackgroundColor( origColor );
        x360_deg_button.setBackgroundColor( origColor );
    }

    // cleanup
    public void cleanup()
    {
        if ( !DEBUG )
        {
            // Set all output pins low    NEEDED???
            blueWire.low();
            pinkWire.low();
            yellowWire.low();
            orangeWire.low();
        }
        Log.d( tag, "All CLEAN" );
    }


    /*
     *  Performs calibration of the IR Remote receiver by reading the IR Remote button clicks
     *  and counting the number of zeros returned by remote receiver. This value will be
     *  used to calculate the average pulse value.
     *  This thread runs on a background thread.
     */
    class CalibrateRemoteAsyncTask extends AsyncTask < GpioProcessor.Gpio, Void, Void >
    {
        int counter;

        @Override
        protected Void doInBackground( GpioProcessor.Gpio... params )
        {
            Log.d( tag, "In doInBackground");
            if ( !DEBUG )
            {
                GpioProcessor.Gpio dIn = params[0];

                // Count number of zeros sent by the IR Remote when press a button 5 times
                // This loop will be timed out but the call to calibrateTimeoutHandler.postDelayed()
                // in the IRActivity code.
                while ( true )
                {
                    if ( dIn.getValue() == 0 )
                        counter += 1;

                    // Escape early if cancel() is called
                    if (isCancelled()) break;
                }
            }
            return null;
        }

        @Override
        protected void onPreExecute()
        {
            Log.d( tag, "In onPreExecute");

            super.onPreExecute();
            counter = 0;
        }

        /*
         *  Called after doInBackground() is completed and will run on UI thread therefore you
         *  can access UI components here.
         */
        @Override
        protected void onPostExecute( Void v)
        {   Log.d( tag, "In CalibrateRemoteAsyncTask onPostExecute" );
            HandleResult( );
        }

        /*
         *  Documentation says onCancelled() is called INSTEAD OF onPostExecute(). Doesn't
         *  seem to happen when running on Emulator but DOES happen on DragonBoard. So
         *  include both methods and duplicate function.
         */
        @Override
        protected void onCancelled( Void v )
        {   Log.d( tag, "In CalibrateRemoteAsyncTask onCancelled" );
            HandleResult( );
        }
        /*
         * Handles Result for onPostExecute() and onCancelled()
         */
        protected void HandleResult( )
        {
            // For testing purposes only (i.e., without real remote)
            if ( DEBUG )
                counter = DEFAULT_AVE_PULSE*5;

            avePulse = counter/5;

            Log.d( tag, "counter = "+ counter+ " &  avePulse = "+ avePulse );
            // super.onPostExecute( result );
            resultTV = (TextView) findViewById( R.id.ave_pulse_result_tv );
            resultTV.setText( Integer.toString( avePulse ) );
        }
    }


    /*
     *  Task called to count the number of zeros received from the IR Remote button press(es).
     *  This value will be sent back to the main activity to determine the sub-option to be
     *  executed. The receiving is done on a background thread.
     */
    class RemoteClickCountAsyncTask extends AsyncTask < GpioProcessor.Gpio, Void, Void >
    {
        int counter;

        @Override
        protected Void doInBackground( GpioProcessor.Gpio... params )
        {
            Log.d( tag, "In doInBackground");
            if ( !DEBUG )
            {
                GpioProcessor.Gpio dIn = params[0];

                // Count number of zeros sent by the IR Remote when press a button 5 times
                // This loop will be timed out but the call to calibrateTimeoutHandler.postDelayed()
                // in the IRActivity code.
                while ( true )
                {
                    if ( dIn.getValue() == 0 )
                        counter += 1;

                    // Escape early if cancel() is called
                    if (isCancelled()) break;
                }
            }
            return null;
        }

        @Override
        protected void onPreExecute()
        {
            Log.d( tag, "In onPreExecute");
            super.onPreExecute();
            counter = 0;
        }

        @Override
        protected void onPostExecute( Void v)
        {
            Log.d( tag, "In onPostExecute" );
            HandleResult();
        }

        /*
         *  Documentation says onCancelled() is called INSTEAD OF onPostExecute(). Doesn't
         *  seem to happen when running on Emulator but DOES happen on DragonBoard. So
         *  include both methods and duplicate function.
         */
        @Override
        protected void onCancelled( Void v )
        {
            Log.d( tag, "In RemoteClickCountAsyncTask onCancelled");
            HandleResult();
        }

        protected void HandleResult()
        {
            // For testing purposes only (i.e., without real remote)
            if ( DEBUG )
                counter = DEFAULT_AVE_PULSE;

            RunSelectedOption( counter );
        }
    }
}
