package bdv.util.dvid;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;

import bdv.util.http.HttpRequest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class Node
{
	
	private final String uuid;
	private final Repository repository;
	
	public String getUuid()
	{
		return uuid;
	}
	
	public Node getParent()
	{
		return null; // null for now
	}
	
	public Repository getRepository()
	{
		return repository;
	}
	
	public String getUrl()
	{
		return repository.getServer().getApiUrl() +"/node/" + this.uuid;
	}
	
	public void commit( String note, String[] log ) throws MalformedURLException, IOException
	{
		JsonArray arr = new JsonArray();
		
		for ( String l : log )
			arr.add(  new JsonPrimitive( l ) );
		
		JsonObject json = new JsonObject();
		json.addProperty( "note", note );
		json.add( "log", arr );
		String url = getUrl() + "/commit";
		HttpRequest.postRequestJSON( url, json );
	}
	
	public Node branch( String note ) throws MalformedURLException, UnsupportedEncodingException, IOException
	{
		JsonObject json = new JsonObject();
		json.addProperty( "note", note );
		String url = DvidUrlOptions.getRequestString( getUrl() + "/branch" );
		HttpURLConnection connection = HttpRequest.postRequestJSON( url, json );
		JsonObject response = new Gson().fromJson( new InputStreamReader( connection.getInputStream() ), JsonObject.class );
		connection.disconnect();
		return new Node( response.get( "child" ).getAsString(), this.repository );
	}
	
	public Node( String uuid, Repository repository )
	{
		super();
		this.uuid = uuid;
		this.repository = repository;
	}
	
	public Dataset createDataset( String name, String type, String... sync ) throws MalformedURLException, IOException
	{
		String postUrl = new StringBuilder( repository.getServer().getApiUrl() )
			.append( "/repo/" )
			.append( this.uuid )
			.append( "/instance" )
			.toString()
			;
		
		JsonObject json = new JsonObject();
		json.addProperty( Dataset.POPERTY_DATANAME, name );
		json.addProperty( Dataset.PROPERTY_TYPENAME, type );
		if ( sync.length > 0 )
		{
			StringBuilder syncString = new StringBuilder( sync[ 0 ] );
			for ( int i = 1; i < sync.length; ++i )
			{
				syncString
					.append( "," )
					.append( sync[ i ] )
					;
			}
			json.addProperty( Dataset.PROPERTY_SYNC, syncString.toString() );
		}
		
		HttpRequest.postRequestJSON( DvidUrlOptions.getRequestString( postUrl ), json ).disconnect();
		
		if ( type.compareToIgnoreCase( DatasetKeyValue.TYPE) == 0 )
			return new DatasetKeyValue( this, name );
		
		else if ( type.compareToIgnoreCase( DatasetBlkLabel.TYPE ) == 0 )
			return new DatasetBlkLabel( this, name );
		
		else
			return new Dataset( this, name );
	}
	
	public Dataset[] createMutuallySynchedDatasets( List< SimpleImmutableEntry< String, String > > namesAndTypes ) throws MalformedURLException, IOException
	{
		int length = namesAndTypes.size();
		Dataset[] datasets = new Dataset[ length ];
		for ( int i = 0; i < length; ++i )
		{
			String[] sync = new String[ length - 1 ];
			SimpleImmutableEntry< String, String > pair = namesAndTypes.get( i );
			ArrayList< String > tmp = new ArrayList< String >();
			for ( int k = 0; k < length; ++k )
				if ( k != i )
					tmp.add( namesAndTypes.get( k ).getKey() );
				else
					continue;
			
			tmp.toArray( sync );
			datasets[ i ] = createDataset( pair.getKey(), pair.getValue(), sync );
			
		}
		return datasets;
	}
	
	public static int compareUuids( String uuid1, String uuid2 )
	{
		int length = Math.min( uuid1.length(), uuid2.length() );
		return uuid1.substring( 0, length ).compareTo( uuid2.substring( 0, length ) );
	}
	
	public static boolean uuidEquivalenceCheck( String uuid1, String uuid2 )
	{
		return compareUuids( uuid1, uuid2 ) == 0;
	}
	
	public static < T, U > SimpleImmutableEntry< T, U > toPair( T t, U u )
	{
		return new SimpleImmutableEntry< T, U >( t, u );
	}
	
	public static void main( String[] args ) throws MalformedURLException, IOException
	{
		String url = "http://vm570.int.janelia.org:8080";
		String uuid = "6efb517b5ca64b67b8d53be310a9bca4";
		Repository repo = new Repository( url, uuid );
		System.out.println( repo.getInfo() );
		Node n2 = new Node( "6efb517b5ca64b67b8d53be310a9bca4", repo );
		Node child = n2.branch( "new branch" );
		ArrayList< SimpleImmutableEntry< String, String > > al = 
				new ArrayList< SimpleImmutableEntry< String, String > >();
		al.add( toPair( "set1", "labelblk" ) );
		al.add( toPair( "set2", "keyvalue" ) );
		al.add( toPair( "set3", "labelblk" ) );
		child.createMutuallySynchedDatasets( al );
	}
	
}