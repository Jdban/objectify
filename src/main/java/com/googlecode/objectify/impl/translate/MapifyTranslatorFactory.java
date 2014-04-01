package com.googlecode.objectify.impl.translate;

import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.annotation.Mapify;
import com.googlecode.objectify.impl.Path;
import com.googlecode.objectify.impl.TypeUtils;
import com.googlecode.objectify.mapper.Mapper;
import com.googlecode.objectify.repackaged.gentyref.GenericTypeReflector;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;


/**
 * <p>This takes a datastore collection and converts it to a POJO Map by letting you select out the key value
 * using a class of your own devising. The values will be written to the collection, not the keys.</p>
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
public class MapifyTranslatorFactory implements TranslatorFactory<Map<Object, Object>, Collection<Object>>
{
	@Override
	public Translator<Map<Object, Object>, Collection<Object>> create(Type type, Annotation[] annotations, CreateContext ctx, Path path) {
		Mapify mapify = TypeUtils.getAnnotation(Mapify.class, annotations);
		if (mapify == null)
			return null;

		@SuppressWarnings("unchecked")
		final Class<? extends Map<?, ?>> mapType = (Class<? extends Map<?, ?>>)GenericTypeReflector.erase(type);

		if (!Map.class.isAssignableFrom(mapType))
			return null;	// We might be here processing the component type of the mapify map!

		final ObjectifyFactory fact = ctx.getFactory();

		Type componentType = GenericTypeReflector.getTypeParameter(type, Map.class.getTypeParameters()[1]);
		if (componentType == null)	// if it was a raw type, just assume Object
			componentType = Object.class;

		final Translator<Object, Object> componentTranslator = fact.getTranslators().get(componentType, annotations, ctx, path);

		@SuppressWarnings("unchecked")
		final Mapper<Object, Object> mapper = (Mapper<Object, Object>)fact.construct(mapify.value());

		return new TranslatorUsesExistingValue<Map<Object, Object>, Collection<Object>>() {
			@Override
			public Map<Object, Object> load(Collection<Object> node, LoadContext ctx, Path path) throws SkipException {
				Map<Object, Object> map = (Map<Object, Object>)ctx.getExistingValue();

				if (map == null)
					map = (Map<Object, Object>)fact.constructMap(mapType);
				else
					map.clear();

				for (Object child: node) {
					try {
						Object translatedChild = componentTranslator.load(child, ctx, path);

						Object key = mapper.getKey(translatedChild);
						map.put(key, translatedChild);
					}
					catch (SkipException ex) {
						// No prob, just skip that one
					}
				}

				return map;
			}

			@Override
			public Collection<Object> save(Map<Object, Object> pojo, boolean index, SaveContext ctx, Path path) throws SkipException {

				// If it's empty, might as well skip it - the datastore doesn't store empty lists
				if (pojo == null || pojo.isEmpty())
					throw new SkipException();

				Collection<Object> list = new ArrayList<>(pojo.size());

				for (Object obj: pojo.values()) {
					try {
						Object child = componentTranslator.save(obj, index, ctx, path);
						list.add(child);
					}
					catch (SkipException ex) {
						// Just skip that node, no prob
					}
				}

				return list;
			}
		};
	}
}
