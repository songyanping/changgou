package com.changgou.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.changgou.entity.Result;
import com.changgou.goods.feign.SkuFeign;
import com.changgou.goods.pojo.Sku;
import com.changgou.search.dao.SkuEsMapper;
import com.changgou.search.pojo.SkuInfo;
import com.changgou.search.service.SkuService;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ClassName SkuServiceImpl
 * @Description:
 * @Author ning.chai@foxmail.com
 * @Date 2020/8/27 0027
 * @Version V1.0
 **/
@Service
public class SkuServiceImpl implements SkuService {

    @Autowired
    private SkuFeign skuFeign;

    @Autowired
    private SkuEsMapper skuEsMapper;

    /**
     * ElasticsearchTemplate：可以实现索引库的增删改查[高级搜索]
     */
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;


    /**
     * 条件搜索
     * @param searchMap
     * @return Map
     */
    @Override
    public Map search(Map<String, Object> searchMap) {

        /**
         * NativeSearchQueryBuilder：搜索条件构建对象，用于封装各种搜索条件
         */
        //2.创建查询对象 的构建对象
        NativeSearchQueryBuilder nativeSearchQueryBuilder = new NativeSearchQueryBuilder();

        String keywords = null;
        if (!CollectionUtils.isEmpty(searchMap)) {
            keywords = searchMap.get("keywords").toString();
            if (StringUtils.isEmpty(keywords)) {
                //赋值给一个默认的值
                keywords = "华为";
            }
        }

        //3.设置查询的条件
        nativeSearchQueryBuilder.withQuery(QueryBuilders.queryStringQuery(keywords).field("name"));


        NativeSearchQuery query = nativeSearchQueryBuilder.build();
        /**
         * 执行搜索，响应结果给我
         * 1.搜索条件封装对象
         * 2.搜索的结果集（集合数据)需要转换的类型
         * 3.AggregatedPage<SkuInfo>:搜索结果集的封装
         */
        AggregatedPage<SkuInfo> page = elasticsearchTemplate.queryForPage(query, SkuInfo.class);

        //分析数据

        //分页参数-总记录数
        long totalElements = page.getTotalElements();
        //分页参数-总页数
        int totalPages = page.getTotalPages();
        //获取数据结果集
        List<SkuInfo> contents = page.getContent();


        //封装一个Map存储所有数据，并返回
        HashMap<String, Object> resultMap = new HashMap<>();
        resultMap.put("rows",contents);
        resultMap.put("total",totalElements);
        resultMap.put("totalPages",totalPages);


        return resultMap;
    }

    @Override
    public void importData() {
        //Feign调用，查询List<Sku>
        Result<List<Sku>> skuResult = skuFeign.findAll();


        //将List<Sku>转成List<SkuInfo>
        List<SkuInfo> skuInfoList = JSON.parseArray(JSON.toJSONString(skuResult.getData()),SkuInfo.class);

        for (SkuInfo skuInfo : skuInfoList) {
            //获取spec->Map(String)->Map类型({"电视音响效果":"立体声","电视屏幕尺寸":"20英寸","尺码":"165"})
            Map<String,Object> specMap = JSON.parseObject(skuInfo.getSpec(),Map.class);
            //如果需要生成动态的域，只需要将该域存入到一个Map<String,Object>对象中即可，该Map<String,Object>的key会生成一个域，域的名字为该map的key
            //当前Map<String,Object>后面Object的值会作为当前Ｓｋｕ对象该域（key)对应的值
            skuInfo.setSpecMap(specMap);
        }


        //调用Dao实现数据批量导入
        skuEsMapper.saveAll(skuInfoList);
    }
}
