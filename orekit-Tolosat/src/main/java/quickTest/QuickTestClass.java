package quickTest;

import java.awt.List;

import java.io.File;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;

import org.hipparchus.geometry.euclidean.threed.RotationOrder;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.attitudes.AttitudesSequence;
import org.orekit.attitudes.LofOffset;
import org.orekit.bodies.CelestialBodyFactory;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.LOFType;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.BoundedPropagator;
import org.orekit.propagation.EphemerisGenerator;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.EcksteinHechlerPropagator;
import org.orekit.propagation.events.EclipseDetector;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.events.handlers.ContinueOnEvent;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.AngularDerivativesFilter;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.PVCoordinatesProvider;
import java.io.FileWriter;
import java.io.IOException;

public class QuickTestClass{
    


	public static void main(String[] args) {
		// TODO Auto-generated method stub
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
        
        
        final AbsoluteDate initialDate = new AbsoluteDate(2024,07,02,12,0,0, TimeScalesFactory.getUTC());
		double 	sma = 6878.e3;   //sma = 6903.1363e3
	 	double	ecc = 2.e-2; //0.02535084
	 	
		double	inc = 94*Math.PI/180;//CL_op_ssoJ2("i", sma, ecc);
		double	pom = Math.PI/2;
		double	mlh = 6; // MLTAN (hours)
		double	cjd0 = 1;//CL_dat_cal2cjd(2024,07,02,12,0,0); //year, month day
	    double	gom = 1;//CL_op_locTime(cjd0, "mlh", mlh, "ra")
		double	anm = 0;
		
		
		Orbit initialOrbit = new KeplerianOrbit(sma,ecc,inc,pom,gom,anm,PositionAngle.MEAN, FramesFactory.getEME2000(), initialDate,
                Constants.EIGEN5C_EARTH_MU);
		
		int time_step = 8600; // in seconds
		int duration =  365*86400;// in seconds
		
		final SpacecraftState initialState = new SpacecraftState(initialOrbit);
		
		final EcksteinHechlerPropagator propagator = new EcksteinHechlerPropagator(initialOrbit,
                Constants.EIGEN5C_EARTH_EQUATORIAL_RADIUS,
                Constants.EIGEN5C_EARTH_MU, Constants.EIGEN5C_EARTH_C20,
                Constants.EIGEN5C_EARTH_C30, Constants.EIGEN5C_EARTH_C40,
                Constants.EIGEN5C_EARTH_C50, Constants.EIGEN5C_EARTH_C60);
		
		final EphemerisGenerator generator = propagator.getEphemerisGenerator();
	

        // Propagation with storage of the results in an integrated ephemeris
        final SpacecraftState finalState = propagator.propagate(initialDate.shiftedBy(duration));
	
		
        //Create Ephemeris
        
        final BoundedPropagator ephemeris = generator.getGeneratedEphemeris();
        
        System.out.format("%nEphemeris defined from %s to %s%n", ephemeris.getMinDate(), ephemeris.getMaxDate());
        
        
        ArrayList<KeplerianOrbit> orbitList = new ArrayList<>();
        ArrayList<String> dataLines = new ArrayList<>();
       
        double interArgPer  = 0;
        
        try {
            FileWriter myWriter = new FileWriter("testfile.txt");
            
            myWriter.write(" Semi-Major Axis ; Eccentricity ; Inclination ; Argument of the perigee ; Right Ascension of the Ascending node\n");

            //Get values from ephemeris
            
        for (int i = 0; i < duration/time_step; i= i + 1)	{
        	AbsoluteDate intermediateDate = initialDate.shiftedBy(time_step*i);
            SpacecraftState intermediateState = ephemeris.propagate(intermediateDate);
            orbitList.add(new KeplerianOrbit(intermediateState.getOrbit()));
            dataLines.add((String.valueOf(orbitList.get(i).getA())));
            //Normalize Argument of the Perigee
            if (orbitList.get(i).getRightAscensionOfAscendingNode() < - 2*Math.PI) {
            	interArgPer = orbitList.get(i).getRightAscensionOfAscendingNode() + 2*Math.PI;
            }
            else if (orbitList.get(i).getRightAscensionOfAscendingNode() > 2*Math.PI) {
            	interArgPer = orbitList.get(i).getRightAscensionOfAscendingNode() - 2*Math.PI;
            }
            else {
            	interArgPer = orbitList.get(i).getRightAscensionOfAscendingNode();
            }
            //Print to File
            System.out.println(orbitList.get(i).getA()); 
            myWriter.write(String.valueOf(orbitList.get(i).getA()) + ";" + String.valueOf(orbitList.get(i).getE()) + ";" + String.valueOf(orbitList.get(i).getI()) + ";" + String.valueOf(interArgPer) + ";" + String.valueOf(orbitList.get(i).getRightAscensionOfAscendingNode()) + "\n");
        }
        myWriter.close();
        }
        catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
          }
            
        System.out.println("Successfully wrote to the file.");
        System.out.println(ephemeris.getManagedAdditionalStates());
		}
		
		catch (OrekitException oe) {
            System.err.println(oe.getLocalizedMessage());
        }

		    
	}
}