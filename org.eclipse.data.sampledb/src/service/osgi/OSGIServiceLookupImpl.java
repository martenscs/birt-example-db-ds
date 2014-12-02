package service.osgi;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import service.ServiceLookup;

public class OSGIServiceLookupImpl implements ServiceLookup {
	BundleContext ctx;

	public OSGIServiceLookupImpl(BundleContext ctx) {
		this.ctx = ctx;
	}

	public Object getService(String name) {
		ServiceReference ref = ctx.getServiceReference(name);
		return ctx.getService(ref);
	}
}