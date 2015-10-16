package bdv.bigcat;

import static bdv.bigcat.CombinedImgLoader.SetupIdAndLoader.setupIdAndLoader;

import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.http.HTTPException;

import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.display.ScaledARGBConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.ui.TransformEventHandler;
import net.imglib2.ui.TransformEventHandler3D;
import net.imglib2.ui.TransformEventHandlerFactory;
import net.imglib2.ui.TransformListener;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import bdv.BigDataViewer;
import bdv.bigcat.composite.ARGBCompositeAlphaYCbCr;
import bdv.bigcat.composite.Composite;
import bdv.bigcat.composite.CompositeCopy;
import bdv.bigcat.ui.RandomSaturatedARGBStream;
import bdv.img.cache.Cache;
import bdv.img.dvid.DvidGrayscale8ImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.dvid.DatasetKeyValue;
import bdv.util.dvid.Repository;
import bdv.util.dvid.Server;
import bdv.viewer.DisplayMode;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class BigCatBrush
{

	public static class UnsignedByteConverter implements Converter< UnsignedByteType, ARGBType >
	{
		public UnsignedByteConverter(final double min, final double max)
		{
			// super(min, max);
		}
		
		@Override
		public void convert(final UnsignedByteType input, final ARGBType output)
		{
			// output.set(getScaledColor(input.get()));
			int v = input.get();
			output.set(ARGBType.rgba(v, 0, 0, 0));
		}
	}

	// TODO: Move this somewhere else
	static public class RandomAccessibleSource<T extends Type<T>> implements Source< T >
	{
		// protected Img<T> image;
		protected RandomAccessibleInterval< T > accessible;
		protected final T type;
		protected final String name;

		final protected InterpolatorFactory< T, RandomAccessible< T > >[] interpolatorFactories;
		{
			interpolatorFactories = new InterpolatorFactory[]{
					new NearestNeighborInterpolatorFactory< UnsignedByteType >(),
					new ClampingNLinearInterpolatorFactory< UnsignedByteType >()
			};
		}
		
		public RandomAccessibleSource( final RandomAccessibleInterval< T > accessible, final T type, final String name )
		{
			// super(accessible, type, name);
			this.accessible = accessible;
			this.type = type;
			this.name = name;
		}
		
		@Override
		public boolean isPresent( final int t )
		{
			return true;
		}
		
		@Override
		public RandomAccessibleInterval< T > getSource( final int t, final int level )
		{
			return accessible;
		}
		
		@Override
		public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{
			final ExtendedRandomAccessibleInterval< T, RandomAccessibleInterval< T > > extendedSource =
					Views.extendValue( getSource( t,  level ), type );
			switch ( method )
			{
			case NLINEAR :
				return Views.interpolate( extendedSource, interpolatorFactories[ 1 ] );
			default :
				return Views.interpolate( extendedSource, interpolatorFactories[ 0 ] );
			}
		}
		
		@Override
		public void getSourceTransform( int t, int level, AffineTransform3D transform )
		{
//			transform.identity();
			transform.set(1, 0, 0, 3000,
						  0, 1, 0, 2000,
						  0, 0, 1, 3000);
		}
		
		@Override
		public AffineTransform3D getSourceTransform( final int t, final int level )
		{
			final AffineTransform3D transform = new AffineTransform3D();
			getSourceTransform( t, level, transform );
			return transform;
		}
		
		@Override
		public T getType()
		{
			return type;
		}
		
		@Override
		public String getName()
		{
			return name;
		}
		
		@Override
		public VoxelDimensions getVoxelDimensions(){return null;}
		
		@Override
		public int getNumMipmapLevels(){return 1;}
	}
	
	public static class MyTransformEventHandler extends TransformEventHandler3D
	{
		final static private TransformEventHandlerFactory< AffineTransform3D > factory = new TransformEventHandlerFactory< AffineTransform3D >()
		{
			@Override
			public TransformEventHandler< AffineTransform3D > create( final TransformListener< AffineTransform3D > transformListener )
			{
				return new MyTransformEventHandler( transformListener );
			}
		};

		public static TransformEventHandlerFactory< AffineTransform3D > factory()
		{
			return factory;
		}
		
		// private boolean active;
		
		public MyTransformEventHandler( final TransformListener< AffineTransform3D > listener )
		{
			super(listener);
			// active = false;
		}
		
		@Override
		public void mouseDragged( final MouseEvent e )
		{
			if(!e.isAltDown())
				super.mouseDragged(e);
		}
	}
	
	public static void main( final String[] args ) throws JsonSyntaxException, JsonIOException, IOException
	{
		final String url = "http://vm570.int.janelia.org:8080";
		final String labelsBase = "multisets-labels-downscaled-zero-extended-2"; //"multisets-labels-downscaled";
		final String uuid = "4668221206e047648f622dc4690ff7dc";

		final Server server = new Server( url );
		final Repository repo = new Repository( server, uuid );

		final DatasetKeyValue[] stores = new DatasetKeyValue[ 4 ];

		for ( int i = 0; i < stores.length; ++i )
		{
			stores[ i ] = new DatasetKeyValue( repo.getRootNode(), labelsBase + "-" + ( 1 << ( i + 1 ) ) );

			try
			{
				repo.getRootNode().createDataset( stores[ i ].getName(), DatasetKeyValue.TYPE );
			}
			catch ( final HTTPException e )
			{
				e.printStackTrace( System.err );
			}
		}

		try
		{
			System.setProperty( "apple.laf.useScreenMenuBar", "true" );

			/* data sources */
			final DvidGrayscale8ImageLoader dvidGrayscale8ImageLoader = new DvidGrayscale8ImageLoader(
					"http://emrecon100.janelia.priv/api",
					"2a3fd320aef011e4b0ce18037320227c",
					"grayscale" );
			
			final int[] dimensions = new int[]{1024, 1024, 1024};
			final Img<UnsignedByteType> img = new ArrayImgFactory<UnsignedByteType>().create(dimensions, new UnsignedByteType());
			final Cursor<UnsignedByteType> cursor = img.cursor();
			while(cursor.hasNext())
			{
				cursor.fwd();
				UnsignedByteType t = cursor.get();
				t.set(0);
			}

			final SegmentBodyAssignment assignment = new SegmentBodyAssignment();

//			final GoldenAngleSaturatedARGBStream colorStream = new GoldenAngleSaturatedARGBStream();
			final RandomSaturatedARGBStream colorStream = new RandomSaturatedARGBStream( assignment );
			colorStream.setAlpha( 0x30 );
//			final ARGBConvertedLabelsSource convertedLabels =
//					new ARGBConvertedLabelsSource(
//							2,
//							dvidLabelsMultisetImageLoader,
//							colorStream );
			final RandomAccessibleSource<UnsignedByteType> imgSource = new RandomAccessibleSource<UnsignedByteType>(img, new UnsignedByteType(0), "Asdf");

			final CombinedImgLoader imgLoader = new CombinedImgLoader(
					setupIdAndLoader( 0, dvidGrayscale8ImageLoader ) );
			dvidGrayscale8ImageLoader.setCache( imgLoader.cache );
			// dvidLabelsMultisetImageLoader.setCache( imgLoader.cache );

			final TimePoints timepoints = new TimePoints( Arrays.asList( new TimePoint( 0 ) ) );
			final Map< Integer, BasicViewSetup > setups = new HashMap< Integer, BasicViewSetup >();
			setups.put( 0, new BasicViewSetup( 0, null, null, null ) );
			final ViewRegistrations reg = new ViewRegistrations( Arrays.asList(
					new ViewRegistration( 0, 0 ) ) );

			final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( timepoints, setups, imgLoader, null );
			final SpimDataMinimal spimData = new SpimDataMinimal( null, seq, reg );

			final ArrayList< ConverterSetup > converterSetups = new ArrayList< ConverterSetup >();
			final ArrayList< SourceAndConverter< ? > > sources = new ArrayList< SourceAndConverter< ? > >();

			BigDataViewer.initSetups( spimData, converterSetups, sources );

			// final ScaledARGBConverter.ARGB converter = new ScaledARGBConverter.ARGB( 0, 255 );
			final UnsignedByteConverter converter = new UnsignedByteConverter(0, 255);
			
			final SourceAndConverter< UnsignedByteType > soc = new SourceAndConverter< UnsignedByteType >( imgSource, converter );
			sources.add( soc );

			/* composites */
			final ArrayList< Composite< ARGBType, ARGBType > > composites = new ArrayList< Composite<ARGBType,ARGBType> >();
			composites.add( new CompositeCopy< ARGBType >() );
			composites.add( new ARGBCompositeAlphaYCbCr() );
//			composites.add( new ARGBCompositeAlpha() );
			// final HashMap< Source< ? >, Composite< ARGBType, ARGBType > > sourceCompositesMap = new HashMap< Source< ? >, Composite< ARGBType, ARGBType > >();
			// sourceCompositesMap.put( sources.get( 0 ).getSpimSource(), composites.get( 0 ) );
			// sourceCompositesMap.put( sources.get( 1 ).getSpimSource(), composites.get( 1 ) );
			// final AccumulateProjectorFactory< ARGBType > projectorFactory = new CompositeProjector.CompositeProjectorFactory< ARGBType >( sourceCompositesMap );

			final Cache cache = imgLoader.getCache();
			final String windowTitle = "BigCat Brush";
			final BigDataViewer bdv = new BigDataViewer( converterSetups, sources, null, timepoints.size(), cache, windowTitle, null,
					ViewerOptions.options().transformEventHandlerFactory(MyTransformEventHandler.factory()));
//						.accumulateProjectorFactory( projectorFactory )
//						.numRenderingThreads( 16 ) );

			final AffineTransform3D transform = new AffineTransform3D();
			transform.set(
					30.367584357121462, -7.233983582120427E-16, 7.815957561302E-16, -103163.46077512865,
					-8.037759535689243E-17, 30.367584357121462, 7.233983582120427E-16, -68518.45769918368,
					7.815957561302E-16, -8.037759535689243E-17, 30.36758435712147, -120957.47720498207 );
			bdv.getViewer().setCurrentViewerTransform( transform );
			bdv.getViewer().setDisplayMode( DisplayMode.FUSED );

			bdv.getViewerFrame().setVisible( true );

			bdv.getViewer().getDisplay().addHandler(
					new BrushController<UnsignedByteType>(
							bdv.getViewer(),
							imgSource,
							new UnsignedByteType(255),
							10
							));
		}
		catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}
}
