package pl.hycom.pip.messanger.handler.processor;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.hycom.pip.messanger.model.Keyword;
import pl.hycom.pip.messanger.model.Product;
import pl.hycom.pip.messanger.pipeline.PipelineContext;
import pl.hycom.pip.messanger.pipeline.PipelineException;
import pl.hycom.pip.messanger.pipeline.PipelineProcessor;
import pl.hycom.pip.messanger.service.ProductService;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Log4j2
public class LoadBestMatchingProductsProcessor implements PipelineProcessor{

    private static final String PRODUCTS = "products";
    private static final String KEYWORDS_FOUND = "keywordsFound";

    @Autowired
    private ProductService productService;

    @Value("${messenger.recommendation.products-amount}")
    private Integer numberOfProducts;

    @Override
    public int runProcess(PipelineContext ctx) throws PipelineException {
        log.info("Started process of LoadBestMatchingProductsProcessor");

        //TODO: read keywords from context
        Keyword[] keywords = {};
        List<Product> products = findBestMatchingProducts(numberOfProducts, ctx, keywords);
        ctx.put(PRODUCTS, products);

        return 1;
    }

    public List<Product> findBestMatchingProducts(int numberOfProducts, PipelineContext ctx, Keyword... keywords) {
        log.info("Finding best matching products");

        if (keywords.length == 0) {
            return Collections.emptyList();
        }
        List<Product> productsWithKeywords = productService.findAllProductsContainingAtLeastOneKeyword(keywords);
        PriorityQueue<Map.Entry<Product, Long>> productsQueue =
                new PriorityQueue<>((o1, o2) -> (int)(o2.getValue() - o1.getValue()));

        productsWithKeywords.stream().filter(Objects::nonNull).map(product -> new HashMap.SimpleEntry<>(product,
            Arrays.stream(Optional.ofNullable(keywords).orElse(new Keyword[] {})).filter(Objects::nonNull)
                    .distinct().filter(product::containsKeyword).count())).forEach(productsQueue::add);

        List<Product> bestMatchingProducts = new ArrayList<>();
        for (int i = 0; i < numberOfProducts && !productsQueue.isEmpty(); ++i) {
            bestMatchingProducts.add(productsQueue.poll().getKey());
        }
        if (ctx != null) {
            saveKeywordsThatWereInAnyProduct(productsWithKeywords, Arrays.asList(keywords), ctx);
        }
        return bestMatchingProducts;
    }

    private void saveKeywordsThatWereInAnyProduct(List<Product> products, List<Keyword> keywords, PipelineContext ctx) {
        List<Keyword> keywordsToBeSaved = keywords.stream().distinct().filter(keyword -> products.stream()
                .anyMatch(product -> product.containsKeyword(keyword))).collect(Collectors.toList());
        ctx.put(KEYWORDS_FOUND, keywordsToBeSaved);
    }
}