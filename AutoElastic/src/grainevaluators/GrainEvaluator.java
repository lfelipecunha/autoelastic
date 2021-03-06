/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package grainevaluators;

import static autoelastic.AutoElastic.gera_log;
import middlewares.OneManager;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;
import thresholds.Thresholds;
import java.util.ArrayList;

/**
 *
 * @author luiz
 */
public class GrainEvaluator {

    private static final String objname = "grainevaluators.GrainEvaluator";
    private ArrayList<Double> loads;

    static Rengine re;
    static REXP resp;
    static int forecast = 8;
    private double efficience = 1;
    private boolean initialized = false;

    private final OneManager cloudManager;
    private final Thresholds thresholds;


    public GrainEvaluator(OneManager cm, Thresholds t)
    {
        loads = new ArrayList<>();
        cloudManager = cm;
        thresholds = t;

        /*String args[] = {"--no-save"};
        re = Rengine.getMainEngine();
        if (re == null) {
            re = new Rengine(args, true, null);
        }*/
        re = new Rengine(null, true, null);
    }

    public void cycle() {
        saveLoad(cloudManager.getCPULoad());
    }



    public int getNumberOfVms(boolean up)
    {
        int totalVMs = cloudManager.getTotalActiveVms();
        if (loads.size() >= 8) {
            gera_log(objname, "Loads Size: " + loads.size());
            double futureLoad = getFutureLoad();
            double vmCapacity = getVMCapacity();
            double averageLoad = getAverageLoad();

            double expectedLoad = getExpectedLoad(up);

            gera_log(objname, "Future Load: " + futureLoad);
            gera_log(objname, "VM Capacity: " + vmCapacity);
            gera_log(objname, "Average Load: " + averageLoad);
            gera_log(objname, "Expected Load: " + expectedLoad);
            gera_log(objname, "Efficience: " + efficience);
            

             totalVMs = (int)Math.round(futureLoad / vmCapacity * averageLoad / expectedLoad * efficience);
        }
        initialized = true;
        gera_log(objname, "Total of VMs: " + totalVMs);

        return totalVMs;
    }

    private double getVMCapacity()
    {
        double average = getAverageLoad();

        return average / cloudManager.getTotalActiveVms();
    }

    private double getAverageLoad()
    {
        double sum = 0;
        for (int i=2; i< loads.size(); i++) {
            sum += loads.get(i);
        }
        return sum / loads.size();
    }

    public double getFutureLoad()
    {
        float decision_load = 0;
        System.out.println("Get Future Load");
        if (loads.size() >= 8) {
            double list[] = new double[loads.size()];
            for  (int i=0; i < loads.size(); i++) {
                System.out.println("LOAD[" + i + "] = " + loads.get(i));
                list[i] = (double)loads.get(i);
            }
            re.assign("y", list);
            re.eval("fit=arima(y, c(0,2,1))");
            resp = re.eval("f <- predict(fit, " + forecast + ")");
            decision_load = (float) resp.asList().at(0).asDoubleArray()[forecast - 1];
            if(decision_load < 0)
                decision_load = 0;
        }
        System.out.println("Decision load: " + decision_load);
        return decision_load;
    }

    private double getExpectedLoad(boolean up)
    {
        /*
        if (up) {
            return thresholds.getLowerThreshold();
        }
        return thresholds.getUpperThreshold();*/
        return (thresholds.getUpperThreshold() + thresholds.getLowerThreshold()) / 2;
    }

    private void saveLoad(float load)
    {
        if (!Double.isNaN(load)) {
            loads.add((double)load);
            if (loads.size() == 4 && initialized) {
                double average = getAverageLoad();
                double expected = getExpectedLoad(true);
                efficience = efficience + (average/expected - 1);
                gera_log(objname, "Average: " + average);
                gera_log(objname, "Efficience: " + efficience);
            }
        }
    }

    public void reset()
    {
        loads.clear();
    }
}
