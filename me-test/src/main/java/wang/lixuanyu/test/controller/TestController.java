package wang.lixuanyu.test.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

	@RequestMapping("/info")
	public String info(String name){
		System.out.println(name);
		return "成功！";
	}
}
