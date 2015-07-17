package bdv.bigcat.composite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.Type;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;

/**
 *
 * @author Stephan Saalfeld <saalfelds@janelia.hhmi.org>
 */
public class CompositeProjector< A extends Type< A > > extends AccumulateProjector< A, A >
{
	public static class CompositeProjectorFactory< A extends Type< A > > implements AccumulateProjectorFactory< A >
	{
		final private ArrayList< Composite< A, A > > composites = new ArrayList< Composite< A, A > >();

		/**
		 * Constructor with a list (to preserve the order) of
		 * {@link Composite Composites}.
		 *
		 * @param composites
		 */
		public CompositeProjectorFactory( final List< Composite< A, A > > composites )
		{
			this.composites.addAll( composites );
		}

		/**
		 * Constructor with a list (to preserve the order) of
		 * {@link Composite Composites}.
		 *
		 * @param composites
		 */
		public CompositeProjectorFactory( final Composite< A, A >... composites )
		{
			this.composites.addAll( Arrays.asList( composites ) );
		}

		@Override
		public VolatileProjector createAccumulateProjector(
				final ArrayList< VolatileProjector > sourceProjectors,
				final ArrayList< ? extends RandomAccessible< A > > sources,
				final RandomAccessibleInterval< A > target,
				final int numThreads,
				final ExecutorService executorService )
		{
			final CompositeProjector< A > projector = new CompositeProjector< A >(
					sourceProjectors,
					sources,
					target,
					numThreads,
					executorService );

			projector.setComposites( composites );

			return projector;
		}
	}

	final protected ArrayList< Composite< A, A > > composites = new ArrayList< Composite< A, A > >();

	public CompositeProjector(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< ? extends RandomAccessible< A > > sources,
			final RandomAccessibleInterval< A > target,
			final int numThreads,
			final ExecutorService executorService )
	{
		super( sourceProjectors, sources, target, numThreads, executorService );
	}

	public void setComposites( final List< Composite< A, A > > composites )
	{
		this.composites.clear();
		this.composites.addAll( composites );
	}

	@Override
	protected void accumulate( final Cursor< A >[] accesses, final A t )
	{
		for ( int i = 0; i < composites.size(); ++i )
			composites.get( i ).compose( t, accesses[ i ].get() );
	}
}
