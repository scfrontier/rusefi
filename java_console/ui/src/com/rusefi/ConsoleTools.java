package com.rusefi;

import com.fathzer.soft.javaluator.DoubleEvaluator;
import com.rusefi.autodetect.PortDetector;
import com.rusefi.binaryprotocol.BinaryProtocol;
import com.rusefi.config.generated.Fields;
import com.rusefi.io.ConnectionStateListener;
import com.rusefi.io.ConnectionStatusLogic;
import com.rusefi.io.IoStream;
import com.rusefi.io.LinkManager;
import com.rusefi.io.serial.SerialIoStreamJSerialComm;
import com.rusefi.maintenance.ExecHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class ConsoleTools {
    private static Map<String, ConsoleTool> TOOLS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        TOOLS.put("help", args -> printTools());
        TOOLS.put("headless", ConsoleTools::runHeadless);
        TOOLS.put("compile", ConsoleTools::invokeCompileExpressionTool);
        TOOLS.put("ptrace_enums", ConsoleTools::runPerfTraceTool);
        TOOLS.put("functional_test", ConsoleTools::runFunctionalTest);
        TOOLS.put("compile_fsio_file", ConsoleTools::runCompileTool);
        TOOLS.put("firing_order", ConsoleTools::runFiringOrderTool);
        TOOLS.put("reboot_ecu", args -> sendCommand(Fields.CMD_REBOOT));
        TOOLS.put(Fields.CMD_REBOOT_DFU, args -> sendCommand(Fields.CMD_REBOOT_DFU));
    }

    public static void printTools() {
        for (String key : TOOLS.keySet()) {
            System.out.println("Tool available: " + key);
        }
    }

    private static void sendCommand(String command) throws IOException {
        String autoDetectedPort = Launcher.autoDetectPort();
        if (autoDetectedPort == null)
            return;
        IoStream stream = SerialIoStreamJSerialComm.openPort(autoDetectedPort);
        byte[] commandBytes = BinaryProtocol.getTextCommandBytes(command);
        stream.sendPacket(commandBytes, FileLog.LOGGER);
    }


    private static void runPerfTraceTool(String[] args) throws IOException {
        PerfTraceTool.readPerfTrace(args[1], args[2], args[3], args[4]);
    }

    private static void runFiringOrderTool(String[] args) throws IOException {
        FiringOrderTSLogic.invoke(args[1]);
    }

    private static void runCompileTool(String[] args) throws IOException {
        int returnCode = invokeCompileFileTool(args);
        System.exit(returnCode);
    }

    private static void runFunctionalTest(String[] args) throws InterruptedException {
        // passing port argument if it was specified
        String[] toolArgs = args.length == 1 ? new String[0] : new String[]{args[1]};
        RealHwTest.main(toolArgs);
    }

    private static void runHeadless(String[] args) {
        String onConnectedCallback = args.length > 1 ? args[1] : null;
        String onDisconnectedCallback = args.length > 2 ? args[2] : null;

        ConnectionStatusLogic.INSTANCE.addListener(new ConnectionStatusLogic.Listener() {
            @Override
            public void onConnectionStatus(boolean isConnected) {
                if (isConnected) {
                    invokeCallback(onConnectedCallback);
                } else {
                    invokeCallback(onDisconnectedCallback);
                }
            }
        });

        String autoDetectedPort = PortDetector.autoDetectSerial();
        if (autoDetectedPort == null) {
            System.err.println("rusEFI not detected");
            return;
        }
        LinkManager.start(autoDetectedPort);
        LinkManager.connector.connect(new ConnectionStateListener() {
            @Override
            public void onConnectionEstablished() {
                SensorLogger.init();
            }

            @Override
            public void onConnectionFailed() {

            }
        });
    }

    private static void invokeCallback(String callback) {
        if (callback == null)
            return;
        System.out.println("Invoking " + callback);
        ExecHelper.submitAction(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec(callback);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }, "callback");
    }

    private static int invokeCompileFileTool(String[] args) throws IOException {
        /**
         * re-packaging array which contains input and output file names
         */
        return CompileTool.run(Arrays.asList(args).subList(1, args.length));
    }

    private static void invokeCompileExpressionTool(String[] args) {
        if (args.length != 2) {
            System.err.println("input expression parameter expected");
            System.exit(-1);
        }
        String expression = args[1];
        System.out.println(DoubleEvaluator.process(expression).getPosftfixExpression());
    }

    public static boolean runTool(String[] args) throws Exception {
        if (args == null || args.length == 0)
            return false;
        String toolName = args[0];
        ConsoleTool consoleTool = TOOLS.get(toolName);
        if (consoleTool != null) {
            consoleTool.runTool(args);
            return true;
        }
        return false;    }

    interface ConsoleTool {
        void runTool(String args[]) throws Exception;
    }
}
