package quickTest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import org.hipparchus.ode.nonstiff.AdaptiveStepsizeIntegrator;
import org.hipparchus.ode.nonstiff.DormandPrince853Integrator;
import org.hipparchus.util.FastMath;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.errors.OrekitException;
import org.orekit.forces.ForceModel;
import org.orekit.forces.gravity.HolmesFeatherstoneAttractionModel;
import org.orekit.forces.gravity.potential.GravityFieldFactory;
import org.orekit.forces.gravity.potential.NormalizedSphericalHarmonicsProvider;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.IERSConventions;

/** Based on the Orekit tutorial for numerical orbit propagation
 * <p>This tutorial shows the interest of the "step handling" mode which hides the complex
 * internal mechanic of the propagation and just fulfills the user main needs.<p>
 * @author Fabien Maussion
 * @author Pascal Parraud
 */
public class NumericalTest {

    /** Private constructor for utility class. */
    private NumericalTest() {
        // empty
    }

    /** Program entry point.
     * @param args program arguments (unused here)
     */
    static ArrayList<KeplerianOrbit> orbitList = new ArrayList<>();
    
    public static void main(final String[] args) {
        try {

            // configure Orekit
            final File home       = new File(System.getProperty("user.home"));
            final File orekitData = new File(home, "orekit-data");
            if (!orekitData.exists()) {
                System.err.format(Locale.US, "Failed to find %s folder%n",
                                  orekitData.getAbsolutePath());
                System.err.format(Locale.US, "You need to download %s from %s, unzip it in %s and rename it 'orekit-data' for this tutorial to work%n",
                                  "orekit-data-master.zip", "https://gitlab.orekit.org/orekit/orekit-data/-/archive/master/orekit-data-master.zip",
                                  home.getAbsolutePath());
                System.exit(1);
            }
            final DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
            manager.addProvider(new DirectoryCrawler(orekitData));

            // gravitation coefficient
            final double mu =  3.986004415e+14;

            // inertial frame
            final Frame inertialFrame = FramesFactory.getEME2000();

            // Initial date
            final AbsoluteDate initialDate = new AbsoluteDate(2024,07,02,12,0,0,TimeScalesFactory.getUTC());

            // Initial orbit
            final double a = 6878.e3; // semi major axis in meters
            final double e = 2.e-2; // eccentricity
            final double i = 97.4009688*Math.PI/180; // inclination
            final double omega =Math.PI/2; // perigee argument
            final double raan = 0; // right ascention of ascending node
            final double lM = 0; // mean anomaly
            final Orbit initialOrbit = new KeplerianOrbit(a, e, i, omega, raan, lM, PositionAngle.MEAN,
                          inertialFrame, initialDate, mu);

            int datastep = 100; // in seconds
    		int duration =  1600*86400;// in seconds
            
            // Initial state definition
            final SpacecraftState initialState = new SpacecraftState(initialOrbit);

            // Adaptive step integrator with a minimum step of 0.01 and a maximum step of 1000
            final double minStep = 0.01;
            final double maxstep = 1000.0;
            final double positionTolerance = 10.0;
            final OrbitType propagationType = OrbitType.KEPLERIAN;
            final double[][] tolerances =
                    NumericalPropagator.tolerances(positionTolerance, initialOrbit, propagationType);
            final AdaptiveStepsizeIntegrator integrator =
                    new DormandPrince853Integrator(minStep, maxstep, tolerances[0], tolerances[1]);

            // Propagator
            final NumericalPropagator propagator = new NumericalPropagator(integrator);
            propagator.setOrbitType(propagationType);

            // Force Model (reduced to perturbing gravity field)
            final NormalizedSphericalHarmonicsProvider provider =
                    GravityFieldFactory.getNormalizedProvider(10, 10);
            final ForceModel holmesFeatherstone =
                    new HolmesFeatherstoneAttractionModel(FramesFactory.getITRF(IERSConventions.IERS_2010,
                                                                                true),
                                                          provider);
     
            // Add force model to the propagator
            propagator.addForceModel(holmesFeatherstone);

            // Set up initial state in the propagator
            propagator.setInitialState(initialState);

            // Set up a step handler
            propagator.getMultiplexer().add(60., new TestStepHandler());

            // Extrapolate from the initial to the final date
            final SpacecraftState finalState = propagator.propagate(initialDate.shiftedBy(duration));
            final KeplerianOrbit o = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(finalState.getOrbit());
            System.out.format(Locale.US, "Final state:%n%s %12.3f %10.8f %10.6f %10.6f %10.6f %10.6f%n",
                              finalState.getDate(),
                              o.getA(), o.getE(),
                              FastMath.toDegrees(o.getI()),
                              FastMath.toDegrees(o.getPerigeeArgument()),
                              FastMath.toDegrees(o.getRightAscensionOfAscendingNode()),
                              FastMath.toDegrees(o.getTrueAnomaly()));
            
            new WriteToFile("output/numericaltest.txt",datastep);
            
        } catch (OrekitException oe) {
            System.err.println(oe.getLocalizedMessage());
        }
    }

    /** Specialized step handler.
     * <p>This class extends the step handler in order to print on the output stream at the given step.<p>
     * @author Pascal Parraud
     */
    private static class TestStepHandler implements OrekitFixedStepHandler {

        /** Simple constructor.
         */
        TestStepHandler() {
            //private constructor
        }

        /** {@inheritDoc} */
        @Override
        public void init(final SpacecraftState s0, final AbsoluteDate t, final double step) {
        		 System.out.println("File initialized");
        		 System.out.println("          date                a           e" +
                               "           i         \u03c9          \u03a9" +
                               "          \u03bd");
        }

        /** {@inheritDoc} */
        @Override
        public void handleStep(final SpacecraftState currentState) {
            final KeplerianOrbit o = (KeplerianOrbit) OrbitType.KEPLERIAN.convertType(currentState.getOrbit());
            System.out.format(Locale.US, "%s %12.3f %10.8f %10.6f %10.6f %10.6f %10.6f%n",
                              currentState.getDate(),
                              o.getA(), o.getE(),
                              FastMath.toDegrees(o.getI()),
                              FastMath.toDegrees(o.getPerigeeArgument()),
                              FastMath.toDegrees(o.getRightAscensionOfAscendingNode()),
                              FastMath.toDegrees(o.getTrueAnomaly()));
            orbitList.add(o);
        }

        /** {@inheritDoc} */
        @Override
        public void finish(final SpacecraftState finalState) {
            System.out.println("this was the last step ");
            System.out.println();
        }

    }

    private static class WriteToFile{
    	
     WriteToFile(String filename, int dataStep) {
    		 try {
    			 FileWriter myWriter = new FileWriter(filename);
    			 int i = -1;
    			 int previ = 0;
    			 for(KeplerianOrbit o : orbitList) {
    				 i += 1;
    				 if( i-previ == 0 || i-previ == dataStep) {
    					 myWriter.write(String.valueOf(o.getA()) + ";"
    					     + String.valueOf(o.getE()) + ";" 
    					     + String.valueOf(FastMath.toDegrees(o.getI())) + ";" 
    					     + String.valueOf(FastMath.toDegrees(o.getPerigeeArgument())) + ";" 
    					     + String.valueOf(FastMath.toDegrees(o.getTrueAnomaly())) + "\n");
    					 previ = i;
    				 }
    			 }
    			 myWriter.close();
    			 
    		 }
    		 catch (IOException e) {
                 System.out.println("An error occurred.");
                 e.printStackTrace();
               }
    	    }
    	
    }
}
