package net.floodlightcontroller.rfproxy.IPC.MongoIPC;

//import java.net.UnknownHostException;

import java.net.UnknownHostException;

import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessage;
import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessageFactory;
import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessageProcessor;
import net.floodlightcontroller.rfproxy.IPC.IPC.IPCMessageService;
import net.floodlightcontroller.rfproxy.IPC.Tools.fields;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

/** An IPC message service that uses MongoDB as its back end. */
public class MongoIPCMessageService extends IPCMessageService implements fields {

	/**
	 * Creates and starts an IPC message service using MongoDB.
	 * 
	 * @param address
	 *            the address and port of the mongo server in the format
	 *            address:port
	 * @param db
	 *            the name of the database to use
	 * @param id
	 *            the ID of this IPC service user
	 */
	public MongoIPCMessageService(String address, String db, String id) {
		this.set_id(id);
		this.db = db;
		this.address = address;
		try {
			this.producerConnection = new Mongo(this.address);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.out.println("Connection Error!");
		}
	};

	/**
	 * Listen to messages. Empty messages are built using the factory, populated
	 * based on the received data and sent to processing by the processor. The
	 * method can be blocking or not.
	 * 
	 * @param channelId
	 *            the channel to listen to messages on
	 * 
	 * @param factory
	 *            the message factory
	 * 
	 * @param processor
	 *            the message processor
	 * 
	 * @param block
	 *            true if blocking behavior is desired, false otherwise
	 */
	public void listen(final String channelId, final IPCMessageFactory factory,
			final IPCMessageProcessor processor, boolean block) {

		/** Creates and run a new thread for capture new messages */
		Thread thread = new Thread("listenWorker") {
			public void run() {
				listenWorker(channelId, factory, processor);
			}
		};

		thread.start();

		if (block) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				System.out.println("Thread creation error!");
			}
		}

	};

	/**
	 * Send a message to another user on a channel.
	 * 
	 * @param channelId
	 *            the channel to send the message to
	 * 
	 * @param to
	 *            the user to send the message to
	 * 
	 * @param msg
	 *            the message
	 * 
	 * @return true if the message was sent, false otherwise
	 */
	public boolean send(String channelId, String to, IPCMessage msg) {

		/* Create ns string */
		String ns = this.db + "." + channelId;

		/* Create a channel */
		this.createChannel(producerConnection, ns);

		/* Create a channel */
		DB db = producerConnection.getDB(this.db);

		/* Get collection from DB */
		DBCollection coll = db.getCollection(channelId);

		/* Create a DBObject */
		BasicDBObject doc = new BasicDBObject();

		doc.put(fields.FROM_FIELD, this.get_id());
		doc.put(fields.TO_FIELD, to);
		doc.put(fields.TYPE_FIELD, msg.get_type());
		doc.put(fields.READ_FIELD, false);
		doc.put(fields.CONTENT_FIELD, msg.to_DBObject());

		/* Insert DBObject in collection */
		coll.insert(doc);

		return true;
	};

	private String db;
	private String address;
	private Mongo producerConnection;

	/** Check to incoming messages */
	public void listenWorker(String channelId, IPCMessageFactory factory,
			IPCMessageProcessor processor) {
		try {
			/* Create a connection */
			Mongo connection = new Mongo(this.address);

			/* Create ns string */
			String ns = this.db + "." + channelId;

			/* Create a channel */
			this.createChannel(connection, ns);

			/* Get DB from connection */
			DB db = connection.getDB(this.db);

			/* Get collection from DB */
			DBCollection collection = db.getCollection(channelId);

			/* Looping for read the messages in collection */
			while (true) {
				/* Create a query */
				BasicDBObject query = new BasicDBObject();

				query.put(fields.TO_FIELD, this.get_id());
				query.put(fields.READ_FIELD, false);

				/* Create cursor */
				DBCursor cursor = collection.find(query);

				while (cursor.hasNext()) {	
					DBObject envelope = cursor.next();

					IPCMessage msg = factory
							.buildForType(Integer.valueOf(envelope.get(
									fields.TYPE_FIELD).toString()));
					
					msg.from_DBObject(envelope);

					processor.process(envelope.get(fields.FROM_FIELD)
							.toString(), this.get_id(), channelId, msg);

					/* Update fields read to true, indicating message read */
					DBObject envelope_atualizado = envelope;
					envelope_atualizado.put(fields.READ_FIELD, true);

					collection.update(new BasicDBObject().append("_id",
							envelope.get("_id")), envelope_atualizado);

				}

				Thread.sleep(50);
			}

		} catch (Exception e) {
			System.out.println("Error in listenWorker");
		}

	}

	/**
	 * Create a new channel of communication. A channel is basically a
	 * collection in Mongo DB.
	 * 
	 * @param con
	 * @param ns
	 */
	private void createChannel(Mongo con, String ns) {
		/* Parser ns to extract database name and collection name */
		String field[] = ns.split("\\.");

		/* Get the database */
		DB db = con.getDB(field[0]);

		/* Create a collection */
		DBCollection collection = db.getCollection(field[1]);

		/* Create a index */
		collection.createIndex(new BasicDBObject("_id", 1));
		collection.createIndex(new BasicDBObject(fields.TO_FIELD, 1));

	}

	public String get_id() {
		return this.id;
	}

	public void set_id(String id) {
		this.id = id;
	}
}
