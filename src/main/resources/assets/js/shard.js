var app = angular.module('main', []).
  controller('ShardCtrl', function($scope, $http) {
    $scope.meta = {'shard': '(unknown shardid)'};

    $scope.refresh = function() {
        $http.get('/api/shard/meta').success(function(data, status, headers, config) {
            $scope.files = data;
        });
    };
});
